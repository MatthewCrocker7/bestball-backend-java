package crocker.golf.bestball.core.service.game;

import crocker.golf.bestball.core.repository.DraftRepository;
import crocker.golf.bestball.core.repository.GameRepository;
import crocker.golf.bestball.core.repository.UserRepository;
import crocker.golf.bestball.domain.game.Game;
import crocker.golf.bestball.domain.game.Team;
import crocker.golf.bestball.domain.game.TeamInfo;
import crocker.golf.bestball.domain.game.draft.Draft;
import crocker.golf.bestball.domain.user.UserCredentials;
import crocker.golf.bestball.domain.user.UserCredentialsDto;

import java.util.List;
import java.util.stream.Collectors;

public class InfoService {

    private DraftRepository draftRepository;
    private GameRepository gameRepository;
    private UserRepository userRepository;

    public InfoService(DraftRepository draftRepository, GameRepository gameRepository, UserRepository userRepository) {
        this.draftRepository = draftRepository;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
    }

    public List<TeamInfo> getTeamInfo(UserCredentialsDto userCredentialsDto) {
        String email = userCredentialsDto.getEmail();
        UserCredentials userCredentials = userRepository.findByEmail(email);

        List<Team> teams = gameRepository.getTeamsByUserId(userCredentials.getUserId());

        return enrichTeamInfo(teams);
    }

    private List<TeamInfo> enrichTeamInfo(List<Team> teams) {
        return teams.stream().map(team -> TeamInfo.builder()
            .teamId(team.getTeamId())
            .draft(teamInfoDraftEnrichment(team))
            .game(gameRepository.getLatestGameByGameId(team.getGameId()))
            .build()
        ).collect(Collectors.toList());
    }

    private Draft teamInfoDraftEnrichment(Team team) {
        Draft draft = draftRepository.getLatestDraftById(team.getDraftId());
        List<Team> teams = gameRepository.getTeamsByDraftId(team.getDraftId());
        draft.setTeams(teams);
        return draft;
    }
}
