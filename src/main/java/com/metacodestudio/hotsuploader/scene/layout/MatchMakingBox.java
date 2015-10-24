package com.metacodestudio.hotsuploader.scene.layout;

import com.metacodestudio.hotsuploader.models.Account;
import com.metacodestudio.hotsuploader.models.LeaderboardRanking;
import io.datafx.controller.ViewController;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * @author Eivind Vegsundvåg
 */
@ViewController(value = "MatchMakingBox.fxml")
public class MatchMakingBox extends VBox {

    @FXML
    private Label qmMmr;

    @FXML
    private Label hlMmr;

    @FXML
    private Label tlMmr;

    public void bind(SingleSelectionModel<Account> selectionModel) {
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.intValue() != -1) {
                updateAccountView(selectionModel.getSelectedItem());
            }
        });
    }

    private void updateAccountView(final Account account) {
        final String ifNotPresent = "N/A";
        if (account == null) {
            return;
        }

        final Optional<Integer> quickMatchMmr = readMmr(account.getLeaderboardRankings(), "QuickMatch");
        applyToLabel(quickMatchMmr, qmMmr, ifNotPresent);

        final Optional<Integer> heroLeagueMmr = readMmr(account.getLeaderboardRankings(), "HeroLeague");
        applyToLabel(heroLeagueMmr, hlMmr, ifNotPresent);

        final Optional<Integer> teamLeagueMmr = readMmr(account.getLeaderboardRankings(), "TeamLeague");
        applyToLabel(teamLeagueMmr, tlMmr, ifNotPresent);
    }


    private Optional<Integer> readMmr(final List<LeaderboardRanking> leaderboardRankings, final String mode) {
        return leaderboardRankings.stream()
                .filter(ranking -> ranking.getGameMode().equals(mode))
                .map(LeaderboardRanking::getCurrentMmr)
                .findAny();
    }

    private void applyToLabel(final Optional<?> value, final Label applyTo, final String ifNotPresent) {
        if (value.isPresent()) {
            applyTo.setText(String.valueOf(value.get()));
        } else {
            applyTo.setText(ifNotPresent);
        }
    }

}
