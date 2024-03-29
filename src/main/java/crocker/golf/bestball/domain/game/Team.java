package crocker.golf.bestball.domain.game;

import crocker.golf.bestball.domain.enums.game.TeamRole;
import crocker.golf.bestball.domain.game.round.TeamRound;
import crocker.golf.bestball.domain.pga.PgaPlayer;
import crocker.golf.bestball.domain.user.UserInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Builder
@Getter
@Setter
public class Team {

    private UUID teamId;
    private UUID userId;
    private UUID draftId;
    private UUID gameId;
    private UUID tournamentId;

    private UserInfo userInfo;
    private TeamRole teamRole;
    private Integer draftPick;

    private PgaPlayer golferOne;
    private PgaPlayer golferTwo;
    private PgaPlayer golferThree;
    private PgaPlayer golferFour;

    private Integer toPar;
    private Integer totalStrokes;

    private List<TeamRound> teamRounds;

    public List<PgaPlayer> getGolfersAsList() {
        return Arrays.asList(golferOne, golferTwo, golferThree, golferFour);
    }

}
