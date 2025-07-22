package com.skillup.async;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.datetime.standard.DateTimeFormatterFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.skillup.dto.ResultTicket;
import com.skillup.dto.SaleResult;
import com.skillup.dto.UserWin;
import com.skillup.dto.UserWinning;
import com.skillup.entity.Game;
import com.skillup.entity.GameTableMgt;
import com.skillup.entity.GeneralGameSettings;
import com.skillup.entity.Result;
import com.skillup.entity.ResultCalculations;
import com.skillup.entity.ResultDetails;
import com.skillup.entity.ResultHelper;
import com.skillup.entity.Ticket;
import com.skillup.entity.TicketDetails;
import com.skillup.repos.GameTableMgtRepo;
import com.skillup.service.GameService;
import com.skillup.service.GameTableMgtService;
import com.skillup.service.GeneralGameSettingsService;
import com.skillup.service.ResultCalculationService;
import com.skillup.service.ResultDetailsService;
import com.skillup.service.ResultHelperService;
import com.skillup.service.ResultService;
import com.skillup.service.TicketDetailsService;
import com.skillup.service.TicketService;
import com.skillup.utility.DateUtility;
import com.skillup.utility.ResultCalculator;
import com.skillup.utility.SchedularSharedClass;
import com.skillup.utility.SkillUpUtility;

import lombok.extern.slf4j.Slf4j;

@Component
@EnableAsync
@Slf4j
public class ScheduledResultAndGameCreation {
	
	@Autowired
	private GeneralGameSettingsService generalGameSettingsServiceImpl;
	
	@Autowired 
	private ResultCalculationService resultCalService;
	
	@Autowired
	private ResultHelperService resultHelperService;
	
	@Autowired
	private ResultService resultService;
	
	@Autowired
	private ResultDetailsService resultDetailsService;
	
	@Autowired
	private GameService gameService;
	
	@Autowired
	private TicketDetailsService ticketDetailsService;
	
	@Autowired
	private GameTableMgtRepo gameTableMgtRepo;	 
	
	@Autowired
	GameTableMgtService gameTableMgtService;
	
	@Autowired
	private TicketService ticketService;
	
	@Value("${skillup.2d-multiplier}")
	private Integer multiplier;
	
    @Scheduled(fixedRate = 1000)
	public void scheduleFixedRateTaskAsync() throws InterruptedException {
		LocalDate localDate = LocalDate.now();
		LocalTime time = LocalTime.now().plusSeconds(5);
		String result = localDate + "-" + String.format("%02d", time.getHour()) + ":"
				+ String.format("%02d", time.getMinute());
		if (SchedularSharedClass.getSchedularSharedInstance().getTimeToAddTenKaDum().contains(time.getMinute()+"")) {
			log.info("Calculation result called [{}]", result);
			calculateResult(localDate + "",
					String.format("%02d", time.getHour()) + ":" + String.format("%02d", time.getMinute()), String.format("%02d", time.getHour()) + ":00", result);
			updateTicketsWithWinning();
			Thread.sleep(30000);
		} 
		Thread.sleep(2000);
	}
    
    @Async
    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void scheduleFixedRateTaskCreateGameAsync() throws InterruptedException {
    	LocalDate localDate = LocalDate.now();
    	List<Game> games;
		try {
			games = gameService.getAllGamesByDate(DateUtility.getDateFromYYYYMMDD(localDate+""));
			if(games == null || games.size() == 0) {
	    		createGamesForDay();
	    		System.out.println("Game created for the day");
	    	}
		} catch (ParseException e) {
			log.error("Exception: ", e);
		}
    	
    }
    
    
    @Async
    @Scheduled(fixedRate = 1000 * 60 * 5)
    public void scheduleFixedRateUpdateWinningAsync() throws InterruptedException {
		try {
			updateTicketsWithWinning();
		} catch (Exception e) {
			log.error("Exception: ", e);
		}
    }
    
    private void updateTicketsWithWinning() {
    	log.info("Winning updated");
    	Result result = resultService.getLastResult();
    	if(ObjectUtils.isNotEmpty(result)) {
    		List<ResultDetails> resultDetails = resultDetailsService.getAllResultDetailsByResult(result);
        	Set<Long> ticketIds = resultDetails.stream()
    				 .map(s->Long.parseLong(s.getTicketNumbers()+""))
    				 .collect(Collectors.toSet());
        	List<TicketDetails> ticket = ticketDetailsService.findAllTicketsByGameId(result.getGameId());
    		List<TicketDetails> tickets =  ticket.stream().filter(p->ticketIds.contains(Long.parseLong(p.getTicketNumbers()+""))).collect(Collectors.toList());
    		if (tickets.size() > 0) {
    			tickets.stream().forEach(s -> s.setWiningPoints(s.getQuantity() * s.getMultiplier() * 90));
    			ticketDetailsService.saveAll(tickets);
    		}
    	}
    }
    
    //Used to add tckets for game in result calculation table
//    private void addTicketsForGame() {
//    	List<Ticket> ticket = ticketService.getTicketByGame(gameService.getGameById(1316L));
//		for (Ticket ticket2 : ticket) {
//			List<TicketDetails> ticketDetails = ticketDetailsService.getAllTicketsByTicketId(ticket2);
//			addResultCalculations(ticket2, ticketDetails);
//		}
//		System.out.println("Done");
//		try {
//			Thread.sleep(20000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//    }
    
    
	private void calculateResult(String localDate, String time, String hourStartGameTime, String storeResult) {
		try {
			Result existResult = resultService.getAllResultByDateAndTime(DateUtility.getDateFromYYYYMMDD(localDate),
					time);
			if (existResult == null) {
				
				long timemilies = System.currentTimeMillis();
				
				Game game = gameService.getGameByDateAndTime(DateUtility.getDateFromYYYYMMDD(localDate), time);
				
				Game hourStartGame = gameService.getGameByDateAndTime(DateUtility.getDateFromYYYYMMDD(localDate), hourStartGameTime);
				
				if(game == null) {
					log.info("Calculation result game not created [{}]", storeResult);
					return;
				}
				existResult = resultService.getResultByGameId(game.getGameId());
				if(existResult != null) {
					log.info("Result already generated game id {} stored result [{}]", game.getGameId(), storeResult);
					return;
				}
				GameTableMgt gameTableMgt = gameTableMgtRepo.findAll().get(0); 
				gameTableMgt.setGameId(game.getGameId());
				gameTableMgtRepo.save(gameTableMgt);
				GeneralGameSettings setting = generalGameSettingsServiceImpl.getSettings();
				double totalPurchase = resultCalService.getTotalPurchasePointsByGame(game.getGameId());
				int target = (int) ((totalPurchase / 100) * setting.getWinningPercentage());
				
				log.info("time taken in milies for fetch setting {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();
			
				
				log.info("time taken in milies for fetch ticketsByUser {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();
				
				List<Object> tickitsByUsernew = ticketService.ticketsGameWithUser(game.getGameId());
				Map<Long, Set<Integer>> ticketsByUsernew = getTicketsNew(tickitsByUsernew);
				log.info("time taken in milies for fetch ticketsByUsernew {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();
				
				
				List<ResultTicket> result = new ArrayList<ResultTicket>();
				List<ResultCalculations> data = resultCalService
						.findResultCalculations(Integer.parseInt(game.getGameId() + ""));
				
				log.info("time taken in milies for fetch data {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();

				List<ResultHelper> unSeleTickets = resultHelperService
						.findTicketsWithNoSelections(Integer.parseInt(game.getGameId() + ""));
				
				log.info("time taken in milies for fetch unSeleTickets {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();				

				List<SaleResult> salesResults = new ArrayList<>();
				
				if(setting.getHourlyLogic() != null && setting.getHourlyLogic() > 0) {
					log.info("fetch dailyWinningSummary for hourly");
					List<Object> dailyWinningSummaryFromPreviousGame = ticketService.dailyWinningSummaryFromToGame(game.getDate(),
							game.getDate(), game.getGameId(), hourStartGame.getGameId());
					salesResults = getSaleResult(dailyWinningSummaryFromPreviousGame);
				} else {
					log.info("fetch dailyWinningSummary for all day");
					List<Object> dailyWinningSummary = ticketService.dailyWinningSummary(game.getDate(),
							game.getDate(), game.getGameId());
					salesResults = getSaleResult(dailyWinningSummary);
				}
				
				
				log.info("time taken in milies for fetch dailyWinningSummary {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();
				
				List<Object> ticketsPerUser = ticketService.ticketsForGameWithUser(game.getGameId());
				Map<Integer, List<UserWin>> userTicketMap = getTickets(ticketsPerUser);
				
				log.info("time taken in milies for fetch data {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();
				
				Map<Long, UserWinning> userWinning = new HashMap<>(); 
				
				ResultCalculator calculator = new ResultCalculator(data, target, result, unSeleTickets, salesResults, setting.getWinningPercentage(), 
									setting.getWinningNumber(), setting.getResultType(), ticketService, game.getGameId(), userTicketMap, ticketsByUsernew, userWinning);
				calculator.calculateResult();
				result = calculator.getResult();
				
				log.info("time taken in milies for calculate result {}", System.currentTimeMillis() - timemilies);
				timemilies = System.currentTimeMillis();
				
				Result res = new Result();
				res.setGameId(game.getGameId());
				res.setDate(DateUtility.getDateFromYYYYMMDD(localDate));
				res.setResultTime(time);
				log.info("Result details size [{}]", result.size());
				List<ResultDetails> resultDetails = new ArrayList<>(result.size());
				result.stream().forEach(r -> {
					ResultDetails resultD = new ResultDetails();
					if(r.getTicketNumder() < 100) {
					resultD.setTicketNumbers(r.getTicketNumder());
					resultD.setResult(res);
					resultDetails.add(resultD);
					}
				});
				List<ResultDetails> dbResultDetails = resultDetailsService.addResultDetails(resultDetails);
				res.setResultDetails(dbResultDetails);
				resultService.addResult(res);
				setting.setWinningNumber("");
				setting.setGameId(-1);
				generalGameSettingsServiceImpl.save(setting);
				log.info("time taken in milies for save result {}", System.currentTimeMillis() - timemilies);
			}
		} catch (ParseException e) {
			log.error("Exception: ", e);
		}
	}
    
	private List<SaleResult> getSaleResult(List<Object> result) {
		List<SaleResult> salesResults = new ArrayList<SaleResult>(result.size());
		result.stream().forEach(obj -> {
			Object[] row = (Object[]) obj;
			try {
				SaleResult pointSummary = SaleResult.builder()
											.userId(Long.parseLong(row[0].toString()))
											.userName(row[1] == null ? "" : row[1].toString())
											.salePoints(Double.parseDouble(row[2].toString()))
											.totalWinning(Double.parseDouble(row[3].toString()))
											.displayUserId(row[4].toString())
											.commition(Double.parseDouble(row[5].toString()))
											.winPercentage(Integer.parseInt(row[6].toString()))
											.maxWinning(Integer.parseInt(row[7].toString()))
											.build();
				pointSummary.setNetPoints(pointSummary.getSalePoints() - pointSummary.getTotalWinning() - pointSummary.getCommition());
				salesResults.add(pointSummary);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		});
		return salesResults;
	}
	
	private Map<Integer, List<UserWin>> getTickets(List<Object> result) {
		Map<Integer, List<UserWin>> salesResults = new HashMap<>();
		result.stream().forEach(obj -> {
			Object[] row = (Object[]) obj;
			try {
				Integer ticketNumber = Integer.parseInt(row[0].toString());
				if(salesResults.containsKey(ticketNumber)) {
					salesResults.get(ticketNumber).add(UserWin.builder().userId(Long.parseLong(row[2].toString()))
							.winning(Integer.parseInt(row[1].toString())).build());
				} else {
					List<UserWin> userWin = new ArrayList<UserWin>();
					userWin.add(UserWin.builder().userId(Long.parseLong(row[2].toString()))
							.winning(Integer.parseInt(row[1].toString())).build());
					salesResults.put(ticketNumber, userWin);
				}
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		});
		return salesResults;
	}
	
	private Map<Long, Set<Integer>> getTicketsNew(List<Object> result) {
		Map<Long, Set<Integer>> salesResults = new HashMap<>();
		result.stream().forEach(obj -> {
			Object[] row = (Object[]) obj;
			try {
				Long user = Long.parseLong(row[0].toString());
				salesResults.put(user, Arrays.stream(row[1].toString().split(",")).map(Integer::parseInt).collect(Collectors.toSet()));
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		});
		return salesResults;
	}
	
    private void createGamesForDay() {
    	DateTimeFormatter simpleDateFormat = new DateTimeFormatterFactory("HH:mm").createDateTimeFormatter();
		LocalTime time = LocalTime.of(8, 00, 00);
		List<Game> games = new ArrayList<Game>(168);
		for (int i = 1; i <= ((168 * 5) / 5); i++) {
			games.add(new Game(i, time.format(simpleDateFormat), new Date()));
			time = time.plusMinutes(5);
		}
		gameService.addAllgames(games);
    }
    
    
    @Async
	private void addResultCalculations(Ticket ticketPurchange, List<TicketDetails> ticketDetails) {
		List<ResultCalculations> resultCalculationTickets = new ArrayList<ResultCalculations>(
				ticketDetails.size());
		
		ticketDetails.stream().forEach(t -> resultCalculationTickets.add(
				new ResultCalculations(ticketPurchange.getGame().getGameId(),
						SkillUpUtility.findComp(t.getTicketNumbers()),
						ticketPurchange.getUser().getUserId(),
						t.getTicketNumbers(),
						t.getQuantity(),
						t.getQuantity() * t.getMultiplier(),
						(t.getQuantity() * t.getMultiplier()) * 90,
						ticketPurchange.getTicketId())));	
		
		resultCalService.addAll(resultCalculationTickets);
	}

}