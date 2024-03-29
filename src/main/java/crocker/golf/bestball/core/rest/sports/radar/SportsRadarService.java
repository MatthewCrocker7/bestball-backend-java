package crocker.golf.bestball.core.rest.sports.radar;

import crocker.golf.bestball.core.rest.SportsApiService;
import crocker.golf.bestball.core.util.TimeHelper;
import crocker.golf.bestball.domain.exceptions.ExternalAPIException;
import crocker.golf.bestball.domain.pga.PgaPlayer;
import crocker.golf.bestball.domain.pga.sports.radar.SportsRadarTournamentRoundDto;
import crocker.golf.bestball.domain.pga.sports.radar.SportsRadarTournamentSummaryDto;
import crocker.golf.bestball.domain.pga.tournament.Tournament;
import crocker.golf.bestball.domain.pga.tournament.TournamentRound;
import crocker.golf.bestball.domain.pga.sports.radar.SportsRadarScheduleDto;
import crocker.golf.bestball.domain.pga.sports.radar.SportsRadarWorldGolfRankingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SportsRadarService implements SportsApiService {

    private static final Logger logger = LoggerFactory.getLogger(SportsRadarService.class);

    private RestTemplate restTemplate;
    private SportsRadarResponseHelper sportsRadarResponseHelper;

    private final LinkedList<String> keys;

    private final String BASE_URL = "http://api.sportradar.us/golf-t2";

    private final String RANKINGS_URL = "/players/wgr/{0}/rankings.json";
    private final String SCHEDULE_URL = "/schedule/pga/{0}/tournaments/schedule.json";
    private final String TOURNAMENT_SUMMARY_URL = "/summary/pga/{0}/tournaments/{1}/summary.json";
    private final String TOURNAMENT_ROUND_URL = "/scorecards/pga/{0}/tournaments/{1}/rounds/{2}/scores.json";

    public SportsRadarService(RestTemplate restTemplate, SportsRadarResponseHelper sportsRadarResponseHelper, String keys) {
        this.restTemplate = restTemplate;
        this.sportsRadarResponseHelper = sportsRadarResponseHelper;
        this.keys = Stream.of(keys.split(","))
                .collect(Collectors.toCollection(LinkedList::new));
    }

    @Retryable(
        value = { HttpClientErrorException.class },
        maxAttempts = 100,
        backoff = @Backoff(3000)
    )
    @Async
    public Future<List<PgaPlayer>> getWorldRankings() throws ExternalAPIException {
        String url = buildRankingsUrl();
        logger.info("Calling api for world rankings on thread {}", Thread.currentThread().getName());
        try {
            ResponseEntity<SportsRadarWorldGolfRankingDto> responseEntity = restTemplate.exchange(url, HttpMethod.GET, null, SportsRadarWorldGolfRankingDto.class);

            if(responseEntity.getStatusCode()!= HttpStatus.OK || responseEntity.getBody() == null) {
                throw new ExternalAPIException("Sports API is having internal server issues");
            }

            SportsRadarWorldGolfRankingDto sportsRadarWorldGolfRankingDto = responseEntity.getBody();

            List<PgaPlayer> pgaPlayers = sportsRadarResponseHelper.mapResponseToRankings(sportsRadarWorldGolfRankingDto);

            return new AsyncResult<>(pgaPlayers);
        } catch (HttpClientErrorException e) {
            shiftKeys();
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
        }
    }

    @Retryable(
            value = { HttpClientErrorException.class },
            maxAttempts = 100,
            backoff = @Backoff(3000)
    )
    @Async
    public Future<List<Tournament>> getSeasonSchedule() throws ExternalAPIException {
        String url = buildScheduleUrl();
        logger.info("Calling api for season schedule on thread {}", Thread.currentThread().getName());
        try {
            ResponseEntity<SportsRadarScheduleDto> responseEntity = restTemplate.exchange(url, HttpMethod.GET, null, SportsRadarScheduleDto.class);

            if(responseEntity.getStatusCode()!= HttpStatus.OK || responseEntity.getBody() == null) {
                throw new ExternalAPIException("Sports API is having internal server issues");
            }

            SportsRadarScheduleDto scheduleDto = responseEntity.getBody();

            List<Tournament> tournaments = sportsRadarResponseHelper.mapResponseToTournaments(scheduleDto);

            return new AsyncResult<>(tournaments);

        } catch (HttpClientErrorException e) {
            shiftKeys();
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
        }

    }

    @Retryable(
            value = { HttpClientErrorException.class },
            maxAttempts = 100,
            backoff = @Backoff(3000)
    )
    @Async
    public Future<Tournament> getLatestTournamentDetails(Tournament tournament) throws ExternalAPIException {
        String url = buildTournamentSummaryUrl(tournament);
        logger.info("Calling api to update tournament summary {}", tournament.getName());

        try {
            ResponseEntity<SportsRadarTournamentSummaryDto> responseEntity = restTemplate.exchange(url, HttpMethod.GET, null, SportsRadarTournamentSummaryDto.class);

            if(responseEntity.getStatusCode()!= HttpStatus.OK || responseEntity.getBody() == null) {
                throw new ExternalAPIException("Sports API is having internal server issues");
            }

            SportsRadarTournamentSummaryDto summaryDto = responseEntity.getBody();

            Tournament tournamentDetails = sportsRadarResponseHelper.mapResponseToTournamentDetails(summaryDto);

            return new AsyncResult<>(tournamentDetails);
        } catch (HttpClientErrorException e) {
            shiftKeys();
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
        }
    }

    @Retryable(
            value = { HttpClientErrorException.class },
            maxAttempts = 100,
            backoff = @Backoff(3000)
    )
    @Async
    public Future<TournamentRound> updateTournamentRound(Tournament tournament, TournamentRound round) throws ExternalAPIException {
        String url = buildTournamentRoundUrl(tournament, round);
        logger.info("API endpoint: {}", url);
        logger.info("Calling api to update tournament round {}", tournament.getName());

        try {
            ResponseEntity<SportsRadarTournamentRoundDto> responseEntity = restTemplate.exchange(url, HttpMethod.GET, null, SportsRadarTournamentRoundDto.class);

            if(responseEntity.getStatusCode()!= HttpStatus.OK || responseEntity.getBody() == null) {
                throw new ExternalAPIException("Sports API is having internal server issues");
            }

            SportsRadarTournamentRoundDto roundDto = responseEntity.getBody();

            TournamentRound tournamentRound = sportsRadarResponseHelper.mapResponseToTournamentRound(roundDto);

            return new AsyncResult<>(tournamentRound);
        } catch (HttpClientErrorException e) {
            shiftKeys();
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
        }
    }

    private String buildRankingsUrl() {
        int year = TimeHelper.getCurrentSeason();
        return BASE_URL + MessageFormat.format(RANKINGS_URL, Integer.toString(year)) + addKey();
    }

    private String buildScheduleUrl() {
        //TODO: Need to make call for both 2020 and 2021 as the season ends in Sept
        int year = TimeHelper.getCurrentSeason();
        return BASE_URL + MessageFormat.format(SCHEDULE_URL, Integer.toString(year)) + addKey();
    }

    private String buildTournamentSummaryUrl(Tournament tournament) {
        return BASE_URL + MessageFormat.format(TOURNAMENT_SUMMARY_URL, Integer.toString(tournament.getSeason()), tournament.getTournamentId()) + addKey();
    }

    private String buildTournamentRoundUrl(Tournament tournament, TournamentRound tournamentRound) {
        return BASE_URL + MessageFormat.format(TOURNAMENT_ROUND_URL, Integer.toString(tournament.getSeason()), tournament.getTournamentId(), tournamentRound.getRoundNumber()) + addKey();
    }

    private void shiftKeys() {
        logger.error("Api key expired. Shifting keys");
        String key = keys.removeFirst();
        keys.addLast(key);
    }

    private String addKey() {
        return "?api_key=" + keys.getFirst();
    }
}
