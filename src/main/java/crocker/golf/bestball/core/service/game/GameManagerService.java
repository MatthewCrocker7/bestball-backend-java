package crocker.golf.bestball.core.service.game;

import crocker.golf.bestball.core.repository.DraftRepository;
import crocker.golf.bestball.core.repository.GameRepository;
import crocker.golf.bestball.core.repository.PgaRepository;
import crocker.golf.bestball.core.repository.UserRepository;
import crocker.golf.bestball.core.service.user.UserService;
import crocker.golf.bestball.domain.enums.game.GameState;
import crocker.golf.bestball.domain.enums.game.ScoreType;
import crocker.golf.bestball.domain.enums.game.TeamRole;
import crocker.golf.bestball.domain.enums.pga.TournamentState;
import crocker.golf.bestball.domain.exceptions.game.TeamNotAuthorizedException;
import crocker.golf.bestball.domain.game.Game;
import crocker.golf.bestball.domain.game.Team;
import crocker.golf.bestball.domain.game.round.TeamRound;
import crocker.golf.bestball.domain.pga.PgaPlayer;
import crocker.golf.bestball.domain.pga.tournament.HoleScore;
import crocker.golf.bestball.domain.pga.tournament.PlayerRound;
import crocker.golf.bestball.domain.pga.tournament.Tournament;
import crocker.golf.bestball.domain.user.RequestDto;
import crocker.golf.bestball.domain.user.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class GameManagerService {

    private static final Logger logger = LoggerFactory.getLogger(GameManagerService.class);

    private final GameRepository gameRepository;
    private DraftRepository draftRepository;
    private final UserRepository userRepository;
    private final PgaRepository pgaRepository;
    private final UserService userService;

    public GameManagerService(GameRepository gameRepository, DraftRepository draftRepository, UserRepository userRepository, PgaRepository pgaRepository, UserService userService) {
        this.gameRepository = gameRepository;
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
        this.pgaRepository = pgaRepository;
        this.userService = userService;
    }

    public Game loadGame(RequestDto requestDto) {
        UUID gameId = UUID.fromString(requestDto.getGameId());
        Game game = gameRepository.getLatestGameByGameId(gameId);
        UserCredentials userCredentials = userRepository.findByEmail(requestDto.getEmail());

        if (game.getGameState() == GameState.NOT_STARTED) {
            return game;
        }
        return enrichGame(game, userCredentials);
    }

    public void deleteGame(RequestDto requestDto) throws TeamNotAuthorizedException {
        UUID gameId = UUID.fromString(requestDto.getGameId());
        UserCredentials userCredentials = userRepository.findByEmail(requestDto.getEmail());

        Team team = gameRepository.getTeamByUserAndGameId(userCredentials.getUserId(), gameId);

        if (team.getTeamRole() == TeamRole.CREATOR) {
            gameRepository.deleteGame(team);
            draftRepository.deleteDraft(team.getDraftId());
        } else {
            throw new TeamNotAuthorizedException("You're not authorized to delete this game");
        }
    }

    public void updateGames() {
        List<Game> activeGames = gameRepository.getInProgressGames();
        logger.info("Updating scores for {} active games.", activeGames.size());

        List<UUID> gameIds = activeGames.stream()
                .map(Game::getGameId)
                .collect(Collectors.toList());

        List<UUID> tournaments = activeGames.stream()
                .map(game -> game.getTournament().getTournamentId())
                .distinct()
                .collect(Collectors.toList());

        HashMap<UUID, List<Team>> batchTeams = new HashMap<>();
        HashMap<UUID, List<TeamRound>> batchTeamRounds = new HashMap<>();

        logger.info("Updating game scores for {} different tournaments", tournaments.size());
        tournaments.forEach(tournamentId -> {

            List<PlayerRound> playerRounds = pgaRepository.getPlayerRoundsByTournamentId(tournamentId);
            List<Team> teams = gameRepository.getTeamsByTournamentId(tournamentId);

            teams = teams.stream()
                    .filter(team -> gameIds.contains(team.getGameId()))
                    .collect(Collectors.toList());

            teams.forEach(team -> {
                    enrichTeamRounds(team, playerRounds);

                    if (batchTeams.containsKey(team.getGameId())) {
                        batchTeams.get(team.getGameId()).add(team);
                    } else {
                        batchTeams.put(team.getGameId(), new ArrayList<>(Arrays.asList(team)));
                    }

                    if (batchTeamRounds.containsKey(team.getGameId())) {
                        batchTeamRounds.get(team.getGameId()).addAll(team.getTeamRounds());
                    } else {
                        batchTeamRounds.put(team.getGameId(), team.getTeamRounds());
                    }
            });

            Tournament tournament = pgaRepository.getTournamentById(tournamentId);

            if (tournament.getTournamentState() == TournamentState.COMPLETE) {
                completeGame(activeGames, tournament);
            }
        });

        batchTeams.keySet().forEach(gameId -> {
            gameRepository.updateTeamRounds(gameId, batchTeamRounds.get(gameId));
            gameRepository.updateTeams(batchTeams.get(gameId));
        });

    }

    private void completeGame(List<Game> activeGames, Tournament tournament) {

        List<Game> gamesToUpdate = activeGames.stream()
                .filter(game -> game.getTournament().getTournamentId().equals(tournament.getTournamentId()))
                .map(game -> game.updateGameState(GameState.COMPLETE))
                .collect(Collectors.toList());

        gameRepository.updateGames(gamesToUpdate);
    }

    private void enrichTeamRounds(Team team, List<PlayerRound> playerRounds) {
        List<TeamRound> teamRounds = new ArrayList<>();
        for(int roundNumber = 1; roundNumber <= 4; roundNumber++) {
            TeamRound teamRound = getTeamRound(team, playerRounds, roundNumber);

            if(teamRound != null) teamRounds.add(teamRound);
        }

        int toPar = teamRounds.stream().mapToInt(TeamRound::getToPar).sum();
        int strokes = teamRounds.stream().mapToInt(TeamRound::getStrokes).sum();
        team.setTeamRounds(teamRounds);
        team.setToPar(toPar);
        team.setTotalStrokes(strokes);
    }

    private TeamRound getTeamRound(Team team, List<PlayerRound> playerRounds, int roundNumber) {

        logger.info("Trying to update team round for team {} on game {}", team.getTeamId(), team.getGameId());

        List<PlayerRound> rounds = playerRounds.stream()
                .filter(playerRound -> playerRound.getRoundNumber() == roundNumber && playerIsOnTeam(team.getGolfersAsList(), playerRound.getPlayerId()))
                .collect(Collectors.toList());

        TeamRound teamRound = TeamRound.builder()
                .teamId(team.getTeamId())
                .gameId(team.getGameId())
                .tournamentId(team.getTournamentId())
                .roundNumber(roundNumber)
                .build();

        if (rounds.isEmpty()) {
            return null;
        } else {
            teamRound.setRoundId(rounds.get(0).getRoundId());
        }

        enrichScores(teamRound, rounds);

        return teamRound;
    }

    private void enrichScores(TeamRound teamRound, List<PlayerRound> playerRounds) {
        List<HoleScore> teamScores = calculateTeamScores(playerRounds);

        int toPar;
        int strokes;
        int frontNine;
        int backNine;

        strokes = teamScores.stream().mapToInt(HoleScore::getStrokes).sum();
        toPar = strokes - teamScores.stream()
                .filter(holeScore -> holeScore.getScoreType() != ScoreType.TBD)
                .mapToInt(HoleScore::getPar).sum();

        frontNine = teamScores.stream().filter(holeScore -> holeScore.getHoleNumber() <= 9)
                .mapToInt(HoleScore::getStrokes).sum();
        backNine = teamScores.stream().filter(holeScore -> holeScore.getHoleNumber() > 9)
                .mapToInt(HoleScore::getStrokes).sum();

        teamRound.setHoleScores(teamScores);
        teamRound.setToPar(toPar);
        teamRound.setStrokes(strokes);
        teamRound.setFrontNine(frontNine);
        teamRound.setBackNine(backNine);
    }

    private List<HoleScore> calculateTeamScores(List<PlayerRound> rounds) {
        HashMap<Integer, HoleScore> teamScores = new HashMap<>();

        rounds.forEach(playerRound -> playerRound.getScores().forEach(holeScore -> {
            if (teamScores.containsKey(holeScore.getHoleNumber())) {
                teamScores.put(holeScore.getHoleNumber(), getLowestScore(teamScores, holeScore));
            } else {
                teamScores.put(holeScore.getHoleNumber(), holeScore);
            }
        }));

        return teamScores.keySet().stream().map(teamScores::get)
                .collect(Collectors.toList());
    }

    private HoleScore getLowestScore(HashMap<Integer, HoleScore> teamScores, HoleScore holeScore) {
        HoleScore currentBestScore = teamScores.get(holeScore.getHoleNumber());
        if (holeScore.getScoreType() != ScoreType.TBD) {
            if (currentBestScore.getScoreType() == ScoreType.TBD || currentBestScore.getStrokes() > holeScore.getStrokes()) {
                return holeScore;
            }
        }
        return currentBestScore;
    }

    private boolean playerIsOnTeam(List<PgaPlayer> players, UUID playerId) {
        return players.stream().anyMatch(pgaPlayer -> pgaPlayer.getPlayerId().equals(playerId));
    }

    private Game enrichGame(Game game, UserCredentials userCredentials) {
        Game enrichedGame = Game.builder()
                .gameId(game.getGameId())
                .gameState(game.getGameState())
                .gameVersion(game.getGameVersion())
                .gameType(game.getGameType())
                .draftId(game.getDraftId())
                .tournament(game.getTournament())
                .numPlayers(game.getNumPlayers())
                .buyIn(game.getBuyIn())
                .moneyPot(game.getMoneyPot())
                .build();

        enrichTeams(enrichedGame);
        enrichTournament(enrichedGame);

        logger.info("Enriched game {} loaded for {}", game.getGameId(), userCredentials.getEmail());
        return enrichedGame;
    }

    private void enrichTeams(Game game) {
        List<Team> teams = gameRepository.getTeamsByDraftId(game.getDraftId());
        List<PlayerRound> playerRounds = pgaRepository.getPlayerRoundsByTournamentId(game.getTournament().getTournamentId());
        List<TeamRound> allTeamRounds = gameRepository.getTeamRoundsByGameId(game.getGameId());

        teams.forEach(team -> {
            enrichPlayerRounds(team.getGolferOne(), playerRounds);
            enrichPlayerRounds(team.getGolferTwo(), playerRounds);
            enrichPlayerRounds(team.getGolferThree(), playerRounds);
            enrichPlayerRounds(team.getGolferFour(), playerRounds);

            List<TeamRound> teamRounds = allTeamRounds.stream()
                    .filter(teamRound -> teamRound.getTeamId().equals(team.getTeamId()))
                    .collect(Collectors.toList());

            team.setTeamRounds(teamRounds);
            team.setUserInfo(userService.getUserInfoFromUserCredentials(team));
        });

        game.setTeams(teams);
    }

    private void enrichTournament(Game game) {
        Tournament tournament = game.getTournament();

        tournament.setTournamentCourses(pgaRepository.getTournamentCourses(tournament.getTournamentId()));
        tournament.setTournamentRounds(pgaRepository.getTournamentRounds(tournament.getTournamentId()));
    }

    private void enrichPlayerRounds(PgaPlayer pgaPlayer, List<PlayerRound> allPlayerRounds) {
        List<PlayerRound> playerRounds = allPlayerRounds.stream()
                .filter(playerRound -> playerRound.getPlayerId().equals(pgaPlayer.getPlayerId()))
                .collect(Collectors.toList());

        pgaPlayer.setRounds(playerRounds);
    }
}
