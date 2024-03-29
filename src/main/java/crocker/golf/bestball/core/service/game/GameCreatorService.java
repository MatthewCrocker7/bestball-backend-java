package crocker.golf.bestball.core.service.game;

import crocker.golf.bestball.core.draft.DraftManager;
import crocker.golf.bestball.core.repository.GameRepository;
import crocker.golf.bestball.core.repository.PgaRepository;
import crocker.golf.bestball.core.repository.UserRepository;
import crocker.golf.bestball.core.util.TimeHelper;
import crocker.golf.bestball.domain.enums.game.DraftState;
import crocker.golf.bestball.domain.enums.game.GameState;
import crocker.golf.bestball.domain.enums.game.GameType;
import crocker.golf.bestball.domain.enums.game.TeamRole;
import crocker.golf.bestball.domain.exceptions.game.TeamNotAuthorizedException;
import crocker.golf.bestball.domain.game.draft.Draft;
import crocker.golf.bestball.domain.game.Game;
import crocker.golf.bestball.domain.game.GameDto;
import crocker.golf.bestball.domain.game.Team;
import crocker.golf.bestball.domain.pga.tournament.Tournament;
import crocker.golf.bestball.domain.user.RequestDto;
import crocker.golf.bestball.domain.user.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public class GameCreatorService {

    private static final Logger logger = LoggerFactory.getLogger(GameCreatorService.class);

    private final GameValidator gameValidator;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final PgaRepository pgaRepository;
    private final DraftManager draftManager;

    private BigDecimal feeMultiplier;

    public GameCreatorService(GameValidator gameValidator, GameRepository gameRepository, UserRepository userRepository, PgaRepository pgaRepository, DraftManager draftManager) {
        this.gameValidator = gameValidator;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.pgaRepository = pgaRepository;
        this.draftManager = draftManager;

        feeMultiplier = new BigDecimal(0.01);
    }

    public void newGame(GameDto gameDto) {

        gameValidator.validateNewGame(gameDto);

        Draft draft = createNewDraft(gameDto);

        Game game = Game.builder()
                .gameId(UUID.randomUUID())
                .gameState(GameState.NOT_STARTED)
                .gameType(getGameType(gameDto))
                .gameVersion(1)
                .draftId(draft.getDraftId())
                .tournament(getTournament(gameDto))
                .numPlayers(gameDto.getNumPlayers())
                .buyIn(gameDto.getBuyIn())
                .moneyPot(calculatePot(gameDto))
                .build();

        gameRepository.saveNewGame(game);
        logger.info("New game created {}", game.getGameId());

        Team team = makeCreatorTeam(gameDto, game);
        gameRepository.saveNewTeam(team);
    }

    public void joinGame(GameDto gameDto) {
        UserCredentials userCredentials = userRepository.findByEmail(gameDto.getEmail());
        Game game = gameRepository.getLatestGameByGameId(gameDto.getGameId());

        Team team = Team.builder()
                .teamId(UUID.randomUUID())
                .userId(userCredentials.getUserId())
                .draftId(game.getDraftId())
                .gameId(game.getGameId())
                .tournamentId(game.getTournament().getTournamentId())
                .teamRole(TeamRole.PARTICIPANT)
                .build();

        gameRepository.saveNewTeam(team);
    }

    public void addToDraft(RequestDto requestDto) throws TeamNotAuthorizedException {
        String email = requestDto.getEmail();
        String inviteEmail = requestDto.getInviteEmail();
        UUID draftId = UUID.fromString(requestDto.getDraftId());
        UserCredentials userCredentials = userRepository.findByEmail(email);

        Team team = gameRepository.getTeamByUserAndDraftId(userCredentials.getUserId(), draftId);

        if (team.getTeamRole() == TeamRole.CREATOR) {
            addPlayer(inviteEmail, team);
        } else {
            throw new TeamNotAuthorizedException("You're not authorized to invite new players");
        }
    }

    public void addToGame(RequestDto requestDto) throws TeamNotAuthorizedException {
        String email = requestDto.getEmail();
        String inviteEmail = requestDto.getInviteEmail();
        UUID gameId = UUID.fromString(requestDto.getGameId());
        UserCredentials userCredentials = userRepository.findByEmail(email);

        Team team = gameRepository.getTeamByUserAndGameId(userCredentials.getUserId(), gameId);

        if (team.getTeamRole() == TeamRole.CREATOR) {
            addPlayer(inviteEmail, team);
        } else {
            throw new TeamNotAuthorizedException("You're not authorized to invite new players");
        }
    }

    private void addPlayer(String inviteEmail, Team creatorTeam) {
        UserCredentials userCredentials = userRepository.findByEmail(inviteEmail);

        Team team = makeParticipantTeam(userCredentials, creatorTeam);

        gameRepository.saveNewTeam(team);
        logger.info("Successfully added new user {} as team {} to draft {} and game {}", team.getUserId(), team.getTeamId(), team.getDraftId(), team.getGameId());
    }

    private Team makeCreatorTeam(GameDto gameDto, Game game) {
        UserCredentials userCredentials = userRepository.findByEmail(gameDto.getEmail());

        return Team.builder()
                .teamId(UUID.randomUUID())
                .userId(userCredentials.getUserId())
                .draftId(game.getDraftId())
                .gameId(game.getGameId())
                .tournamentId(game.getTournament().getTournamentId())
                .teamRole(TeamRole.CREATOR)
                .build();
    }

    private Team makeParticipantTeam(UserCredentials userCredentials, Team creatorTeam) {
        return Team.builder()
                .teamId(UUID.randomUUID())
                .userId(userCredentials.getUserId())
                .draftId(creatorTeam.getDraftId())
                .gameId(creatorTeam.getGameId())
                .tournamentId(creatorTeam.getTournamentId())
                .teamRole(TeamRole.PARTICIPANT)
                .build();
    }

    private Draft createNewDraft(GameDto gameDto) {
        ZonedDateTime date = gameDto.getDraftDate();
        LocalDateTime localStartDate = TimeHelper.getExactReleaseTime(date);

        Draft draft = Draft.builder()
                .draftId(UUID.randomUUID())
                .startTime(localStartDate)
                .draftState(DraftState.NOT_STARTED)
                .draftVersion(1)
                .currentPick(1)
                .build();

        draftManager.scheduleDraft(draft);
        return draft;
    }

    private Tournament getTournament(GameDto gameDto) {
        return pgaRepository.getTournamentById(gameDto.getTournamentId());
    }

    private GameType getGameType(GameDto gameDto) {
        return GameType.valueOf(gameDto.getGameType().toUpperCase());
    }

    private BigDecimal calculatePot(GameDto gameDto) {
        Integer numPlayers = gameDto.getNumPlayers();
        BigDecimal buyIn = gameDto.getBuyIn();

        BigDecimal totalCash = buyIn.multiply(BigDecimal.valueOf(numPlayers));
        BigDecimal fees = totalCash.multiply(feeMultiplier);

        // save fees?

        return totalCash.subtract(fees);
    }


}
