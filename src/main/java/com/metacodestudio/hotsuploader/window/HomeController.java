package com.metacodestudio.hotsuploader.window;

import com.metacodestudio.hotsuploader.AccountService;
import com.metacodestudio.hotsuploader.files.FileHandler;
import com.metacodestudio.hotsuploader.models.Account;
import com.metacodestudio.hotsuploader.models.Hero;
import com.metacodestudio.hotsuploader.models.LeaderboardRanking;
import com.metacodestudio.hotsuploader.models.ReplayFile;
import com.metacodestudio.hotsuploader.models.stringconverters.HeroConverter;
import com.metacodestudio.hotsuploader.providers.HotsLogsProvider;
import com.metacodestudio.hotsuploader.scene.control.CustomListCellFactory;
import com.metacodestudio.hotsuploader.scene.layout.MatchMakingBox;
import com.metacodestudio.hotsuploader.services.HeroService;
import com.metacodestudio.hotsuploader.utils.*;
import com.metacodestudio.hotsuploader.versions.GitHubRelease;
import com.metacodestudio.hotsuploader.versions.ReleaseManager;
import io.datafx.controller.ViewController;
import io.datafx.controller.flow.action.ActionMethod;
import io.datafx.controller.flow.action.ActionTrigger;
import io.datafx.controller.flow.context.FXMLViewFlowContext;
import io.datafx.controller.flow.context.ViewFlowContext;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.util.Duration;
import javafx.util.StringConverter;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@ViewController(value = "Home.fxml", title = "HotSLogs UploaderFX")
public class HomeController {

    private SimpleHttpClient httpClient;
    @FXMLViewFlowContext
    private ViewFlowContext viewFlowContext;

    @FXML
    private VBox updatePane;
    @FXML
    private Label newVersionLabel;
    @FXML
    private Hyperlink updateLink;

    @FXML
    private ListView<ReplayFile> newReplaysView;

    @FXML
    private Label status;

    @FXML
    private MatchMakingBox matchMakingBox;

    @FXML
    private ImageView logo;

    @FXML
    @ActionTrigger("playerSearch")
    private Button playerSearch;

    @FXML
    private TextField playerSearchInput;

    @FXML
    @ActionTrigger("lookupHero")
    private Button lookupHero;

    @FXML
    private ComboBox<Hero> heroName;

    @FXML
    @ActionTrigger("invalidateExceptions")
    private Button invalidateExceptions;

    private FileHandler fileHandler;
    private DesktopWrapper desktop;
    @FXML
    private Label uploadedReplays;
    @FXML
    private Label newReplaysCount;


    @PostConstruct
    public void init() {
        desktop = new DesktopWrapper();
        viewFlowContext.register(desktop);
        httpClient = viewFlowContext.getRegisteredObject(SimpleHttpClient.class);
        fileHandler = viewFlowContext.getRegisteredObject(FileHandler.class);
        logo.setOnMouseClicked(event -> doOpenHotsLogs());
        fetchHeroNames();
        setPlayerSearchActions();
        bindList();
        setupFileHandler();
        if (fileHandler.isIdle()) {
            setIdle();
        }


        checkNewVersion();
        fileHandler.beginWatch();

        matchMakingBox.init(viewFlowContext);
    }

    private void checkNewVersion() {
        ReleaseManager releaseManager = viewFlowContext.getRegisteredObject(ReleaseManager.class);
        Task<GitHubRelease> task = new Task<GitHubRelease>() {
            @Override
            protected GitHubRelease call() throws Exception {
                Optional<GitHubRelease> newerVersionIfAny = releaseManager.getNewerVersionIfAny();
                if (newerVersionIfAny.isPresent()) {
                    return newerVersionIfAny.get();
                } else {
                    return null;
                }
            }
        };
        task.setOnSucceeded(event -> {
            GitHubRelease newerVersionIfAny = task.getValue();
            if (newerVersionIfAny != null) {
                displayUpdateMessage(newerVersionIfAny);
            }
        });
        new Thread(task).start();
    }

    private void displayUpdateMessage(final GitHubRelease newerVersionIfAny) {
        newVersionLabel.setText(newerVersionIfAny.getTagName());
        updateLink.setOnMouseClicked(value -> {
            try {
                desktop.browse(SimpleHttpClient.encode(newerVersionIfAny.getHtmlUrl()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        updatePane.setVisible(true);
    }

    private void fetchHeroNames() {
        heroName.converterProperty().setValue(new HeroConverter());
        FXUtils.autoCompleteComboBox(heroName, FXUtils.AutoCompleteMode.STARTS_WITH);
        HeroService heroService = new HeroService(httpClient);
        heroService.setOnSucceeded(event -> {
            if (null != heroService.getValue()) {
                heroName.getItems().setAll(heroService.getValue());
            }
        });
        heroService.start();
    }

    private void doOpenHotsLogs() {
        try {
            desktop.browse(SimpleHttpClient.encode("https://www.hotslogs.com/Default"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setPlayerSearchActions() {
        playerSearchInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                try {
                    doPlayerSearch();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @ActionMethod("lookupHero")
    private void doLookupHero() throws IOException {
        Hero hero = this.heroName.getValue();
        if (hero == null) {
            return;
        }
        String heroName = hero.getPrimaryName();
        if (heroName.equals("")) {
            return;
        } else {
            this.heroName.setValue(null);
        }
        desktop.browse(SimpleHttpClient.encode("https://www.hotslogs.com/Sitewide/HeroDetails?Hero=" + heroName));
    }

    @ActionMethod("playerSearch")
    private void doPlayerSearch() throws IOException {
        String playerName = playerSearchInput.getText().replaceAll(" ", "");
        if (playerName.equals("")) {
            return;
        } else {
            playerSearchInput.setText("");
        }
        desktop.browse(SimpleHttpClient.encode("https://www.hotslogs.com/PlayerSearch?Name=" + playerName));
    }

    private void setupFileHandler() {
        fileHandler.setRestartOnFailure(true);
        fileHandler.setOnSucceeded(event -> {
            if (HotsLogsProvider.isMaintenance()) {
                setMaintenance();
            } else if (fileHandler.isIdle()) {
                setIdle();
            } else {
                setUploading();
            }
        });
        fileHandler.setOnFailed(event -> setError());
        fileHandler.start();
    }

    private void bindList() {
        ObservableList<ReplayFile> files = fileHandler.getFiles();
        newReplaysCount.setText(String.valueOf(files.size()));
        files.addListener((ListChangeListener<ReplayFile>) c -> newReplaysCount.setText(String.valueOf(files.size())));
        newReplaysView.setItems(files.sorted(new ReplayFileComparator()));
        newReplaysView.setCellFactory(new CustomListCellFactory(fileHandler));

        uploadedReplays.textProperty().bind(fileHandler.getUploadedCount());
    }

    private void setIdle() {
        status.setText("Idle");
        status.textFillProperty().setValue(Paint.valueOf("#0099DA"));
    }

    private void setMaintenance() {
        status.setText("Maintenance");
        status.textFillProperty().setValue(Paint.valueOf("#FF0000"));
    }

    private void setUploading() {
        status.setText("Uploading");
        status.textFillProperty().setValue(Paint.valueOf("#00B000"));
    }

    private void setError() {
        status.setText("Connection error");
        status.textFillProperty().setValue(Paint.valueOf("#FF0000"));
    }

}
