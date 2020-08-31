package crocker.golf.bestball.core.dao;

import crocker.golf.bestball.core.mapper.TournamentRowMapper;
import crocker.golf.bestball.domain.pga.PgaPlayer;
import crocker.golf.bestball.domain.pga.Tournament;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PgaDao {

    private static final Logger logger = LoggerFactory.getLogger(PgaDao.class);

    private JdbcTemplate jdbcTemplate;

    private final String WORLD_RANKINGS = "WORLD_RANKINGS";
    private final String SEASON_SCHEDULE = "SEASON_SCHEDULE";

    private final String DELETE_RANKINGS = "DELETE FROM " + WORLD_RANKINGS + ";";
    private final String DELETE_SCHEDULE = "DELETE FROM " + SEASON_SCHEDULE + ";";

    private final String UPDATE_RANKINGS = "INSERT INTO " + WORLD_RANKINGS +
            " (PLAYER_ID, PLAYER_RANK, PLAYER_NAME)" +
            " VALUES(?, ?, ?);";

    private final String UPDATE_SCHEDULE = "INSERT INTO " + SEASON_SCHEDULE +
            " (TOURNAMENT_ID, EVENT_TYPE, PGA_SEASON, TOURNAMENT_STATE, TOURNAMENT_NAME," +
            " TOURNAMENT_START_DATE, TOURNAMENT_END_DATE)" +
            " VALUES(?, ?, ?, ?, ?, ?, ?);";

    private final String GET_SCHEDULE_BY_SEASON = "SELECT * FROM " + SEASON_SCHEDULE +
            " WHERE PGA_SEASON=?;";

    private final String GET_TOURNAMENT_BY_ID = "SELECT * FROM " + SEASON_SCHEDULE +
            " WHERE TOURNAMENT_ID=?;";


    public PgaDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateWorldRankings(List<PgaPlayer> pgaPlayers) {
        logger.info("Updating world rankings");

        List<Object[]> params = getPlayerParams(pgaPlayers);

        //TODO: Remove delete scripts. Use namedParameterJdbc and use on conflict update
        jdbcTemplate.execute(DELETE_RANKINGS);
        jdbcTemplate.batchUpdate(UPDATE_RANKINGS, params);
    }

    public void updateSeasonSchedule(List<Tournament> tournaments) {
        logger.info("Updating season schedule");

        List<Object[]> params = getTournamentParams(tournaments);

        jdbcTemplate.execute(DELETE_SCHEDULE);
        jdbcTemplate.batchUpdate(UPDATE_SCHEDULE, params);
    }

    public List<PgaPlayer> getWorldRankings() {
        return Collections.emptyList();
    }

    public List<Tournament> getTournamentsBySeason(int year) {
        Object[] params = new Object[]{year};
        return jdbcTemplate.query(GET_SCHEDULE_BY_SEASON, params, new TournamentRowMapper());
    }

    public Tournament getTournamentById(String tournamentId) {
        Object[] params = new Object[]{tournamentId};
        return jdbcTemplate.queryForObject(GET_TOURNAMENT_BY_ID, params, new TournamentRowMapper());
    }

    private List<Object[]> getPlayerParams(List<PgaPlayer> pgaPlayers) {
        return pgaPlayers.stream().map(pgaPlayer -> new Object[] {
                pgaPlayer.getPlayerId(),
                pgaPlayer.getRank(),
                pgaPlayer.getPlayerName()
        }).collect(Collectors.toList());
    }

    private List<Object[]> getTournamentParams(List<Tournament> tournaments) {
        return tournaments.stream().map(tournament -> new Object[] {
                tournament.getTournamentId(),
                tournament.getEventType().name(),
                tournament.getSeason(),
                tournament.getTournamentState().name(),
                tournament.getName(),
                tournament.getStartDate(),
                tournament.getEndDate(),

        }).collect(Collectors.toList());
    }

}