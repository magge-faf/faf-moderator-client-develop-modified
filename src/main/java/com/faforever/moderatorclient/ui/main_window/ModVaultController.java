package com.faforever.moderatorclient.ui.main_window;

import com.faforever.commons.api.dto.GroupPermission;
import com.faforever.commons.api.dto.Mod;
import com.faforever.commons.api.dto.ModVersionReview;
import com.faforever.commons.api.dto.ModVersionReviewsSummary;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.domain.ModService;
import com.faforever.moderatorclient.config.local.LocalPreferences;
import com.faforever.moderatorclient.mapstruct.ModMapper;
import com.faforever.moderatorclient.mapstruct.ModVersionMapper;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.ViewHelper;
import com.faforever.moderatorclient.ui.domain.ModFX;
import com.faforever.moderatorclient.ui.domain.ModVersionFX;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModVaultController implements Controller<SplitPane> {
    private final FafApiCommunicationService communicationService;
    private final ModService modService;
    private final ModMapper modMapper;
    private final ModVersionMapper modVersionMapper;
    private final LocalPreferences localPreferences;
    private final ObservableList<ModFX> mods = FXCollections.observableArrayList();
    private final ObservableList<ModVersionFX> modVersions = FXCollections.observableArrayList();
    private final ObservableList<ModVersionReview> modVersionReviews = FXCollections.observableArrayList();

    private boolean canEdit = false;
    private String selectedModVersionId;

    public SplitPane root;
    public RadioButton searchModByModNameRadioButton;
    public RadioButton searchModByModVersionUidRadioButton;
    public RadioButton searchModByAuthorIdRadioButton;
    public RadioButton searchModByAuthorNameRadioButton;
    public TextField modSearchTextField;
    public CheckBox excludeHiddenModVersionsCheckbox;
    public ImageView modVersionPreviewImageView;
    public TableView<ModFX> modSearchTableView;
    public TableView<ModVersionFX> modVersionTableView;
    public TableView<ModVersionReview> modVersionReviewTableView;
    public Label modVersionReviewSummaryLabel;
    public Button hideModButton;
    public Button toggleModRecommendationButton;
    public Button toggleModVersionHidingButton;
    public Button toggleModVersionRatingButton;
    public Button removeSelectedReviewButton;

    @Override
    public SplitPane getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        ViewHelper.buildModTableView(modSearchTableView, mods);
        ViewHelper.buildModVersionTableView(modVersionTableView, modVersions);
        buildModVersionReviewTable();
        ViewHelper.ensureColumnIds(modSearchTableView);
        ViewHelper.ensureColumnIds(modVersionTableView);
        LocalPreferences.TabModVault tab = localPreferences.getTabModVault();
        Platform.runLater(() -> {
            ViewHelper.loadColumnLayout(modSearchTableView, tab.getModSearchTableColumnWidths(), tab.getModSearchTableColumnOrder());
            ViewHelper.loadColumnLayout(modVersionTableView, tab.getModVersionTableColumnWidths(), tab.getModVersionTableColumnOrder());
        });

        canEdit = communicationService.hasPermission(GroupPermission.ROLE_ADMIN_MOD);

        modSearchTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            modVersions.clear();

            if (newValue == null) {
                hideModButton.setDisable(true);
                toggleModRecommendationButton.setDisable(true);
            } else {
                modVersions.addAll(newValue.getVersions());
                hideModButton.setDisable(!canEdit);
                toggleModRecommendationButton.setDisable(!canEdit);
            }
        });

        modVersionTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                modVersionPreviewImageView.setImage(null);
                toggleModVersionHidingButton.setDisable(true);
                toggleModVersionRatingButton.setDisable(true);
                selectedModVersionId = null;
                modVersionReviews.clear();
                modVersionReviewSummaryLabel.setText("No version selected");
            } else {
                modVersionPreviewImageView.setImage(new Image(newValue.getThumbnailUrl().toString()));
                toggleModVersionHidingButton.setDisable(!canEdit);
                toggleModVersionRatingButton.setDisable(!canEdit);
                loadModVersionReviews(newValue);
            }
        });

        modVersionReviewTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                removeSelectedReviewButton.setDisable(!canEdit || newValue == null));
    }

    public void onSearchMods() {
        mods.clear();
        modSearchTableView.getSortOrder().clear();

        List<Mod> modsFound = Collections.emptyList();
        String searchPattern = modSearchTextField.getText();
        if (searchModByModNameRadioButton.isSelected()) {
            modsFound = modService.findModsByName(searchPattern, excludeHiddenModVersionsCheckbox.isSelected());
        } else if (searchModByModVersionUidRadioButton.isSelected()) {
            modsFound = modService.findModsByModVersionUid(searchPattern, excludeHiddenModVersionsCheckbox.isSelected());
        } else if (searchModByAuthorIdRadioButton.isSelected()) {
            modsFound = modService.findModsByAuthorId(searchPattern, excludeHiddenModVersionsCheckbox.isSelected());
        } else if (searchModByAuthorNameRadioButton.isSelected()) {
            modsFound = modService.findModsByAuthorName(searchPattern, excludeHiddenModVersionsCheckbox.isSelected());
        }

        mods.addAll(modMapper.map(modsFound));
    }

    public void onHideAllVersions() {
        ModFX mod = modSearchTableView.getSelectionModel().getSelectedItem();

        Assert.notNull(mod, "You can only edit a selected Mod");

        if (ViewHelper.confirmDialog("Bulk mod update", MessageFormat.format(
                "You are about to update {0} mod versions.\n" +
                        "This might take a while and the client will freeze.\n" +
                        "Do you want to update now?",
                mod.getVersions().size()))) {
            mod.getVersions().parallelStream()
                    .peek(mapVersionFX -> mapVersionFX.setHidden(true))
                    .map(modVersionMapper::map)
                    .forEach(modService::patchModVersion);
        }
    }

    public void onToggleModVersionHiding() {
        ModVersionFX modVersion = modVersionTableView.getSelectionModel().getSelectedItem();

        Assert.notNull(modVersion, "You can only edit a selected ModVersion");

        modVersion.setHidden(!modVersion.isHidden());
        modService.patchModVersion(modVersionMapper.map(modVersion));
    }

    public void onToggleModVersionRanking() {
        ModVersionFX modVersion = modVersionTableView.getSelectionModel().getSelectedItem();

        Assert.notNull(modVersion, "You can only edit a selected ModVersion");

        modVersion.setRanked(!modVersion.isRanked());
        modService.patchModVersion(modVersionMapper.map(modVersion));
    }

    public void onToggleModRecommendation() {
        ModFX mod = modSearchTableView.getSelectionModel().getSelectedItem();

        Assert.notNull(mod, "You can only edit a selected Mod");

        mod.setRecommended(!mod.isRecommended());
        modService.patchMod(modMapper.map(mod));
    }

    public void onRemoveSelectedReview() {
        ModVersionReview review = modVersionReviewTableView.getSelectionModel().getSelectedItem();
        Assert.notNull(review, "You can only remove a selected review");
        if (!canEdit) {
            return;
        }

        String reviewer = review.getPlayer() == null ? "unknown reviewer" :
                review.getPlayer().getLogin() + " (ID " + review.getPlayer().getId() + ")";
        if (!ViewHelper.confirmDialog("Remove Mod Vault review", MessageFormat.format(
                "Remove review {0} by {1}? Its rating will be removed from this mod version too.", review.getId(), reviewer))) {
            return;
        }

        removeSelectedReviewButton.setDisable(true);
        CompletableFuture.runAsync(() -> modService.deleteModVersionReview(review.getId()))
                .whenComplete((unused, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        log.error("Failed to remove mod version review {}", review.getId(), throwable);
                        ViewHelper.errorDialog("Review removal failed", "The review was not removed. " + throwable.getMessage());
                        removeSelectedReviewButton.setDisable(!canEdit || modVersionReviewTableView.getSelectionModel().getSelectedItem() == null);
                        return;
                    }
                    ModVersionFX modVersion = modVersionTableView.getSelectionModel().getSelectedItem();
                    if (modVersion != null) {
                        loadModVersionReviews(modVersion);
                    }
                }));
    }

    private void loadModVersionReviews(ModVersionFX modVersion) {
        String modVersionId = modVersion.getId();
        selectedModVersionId = modVersionId;
        modVersionReviews.clear();
        modVersionReviewSummaryLabel.setText("Loading reviews...");
        CompletableFuture.supplyAsync(() -> modService.getModVersionReviewData(modVersionId))
                .whenComplete((reviewData, throwable) -> Platform.runLater(() -> {
                    if (!modVersionId.equals(selectedModVersionId)) {
                        return;
                    }
                    if (throwable != null) {
                        log.error("Failed to load mod version reviews for {}", modVersionId, throwable);
                        modVersionReviewSummaryLabel.setText("Could not load reviews");
                        ViewHelper.errorDialog("Review load failed", "Reviews could not be loaded. " + throwable.getMessage());
                        return;
                    }
                    modVersionReviews.setAll(reviewData.reviews());
                    setReviewSummary(reviewData.summary());
                }));
    }

    private void setReviewSummary(ModVersionReviewsSummary summary) {
        if (summary == null || summary.getAverageScore() == null) {
            modVersionReviewSummaryLabel.setText("No reviews");
            return;
        }
        modVersionReviewSummaryLabel.setText(MessageFormat.format("{0} reviews, average score {1}",
                summary.getReviews(), summary.getAverageScore()));
    }

    private void buildModVersionReviewTable() {
        modVersionReviewTableView.setItems(modVersionReviews);
        addReviewColumn("Review ID", ModVersionReview::getId);
        addReviewColumn("Reviewer", review -> review.getPlayer() == null ? "" : review.getPlayer().getLogin());
        addReviewColumn("Reviewer ID", review -> review.getPlayer() == null ? "" : review.getPlayer().getId());
        addReviewColumn("Score", review -> String.valueOf(review.getScore()));
        addReviewColumn("Text", ModVersionReview::getText);
        addReviewColumn("Created", review -> String.valueOf(review.getCreateTime()));
        addReviewColumn("Updated", review -> String.valueOf(review.getUpdateTime()));
    }

    private void addReviewColumn(String title, java.util.function.Function<ModVersionReview, String> value) {
        javafx.scene.control.TableColumn<ModVersionReview, String> column = new javafx.scene.control.TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setMinWidth(100);
        modVersionReviewTableView.getColumns().add(column);
    }

    public void onSave() {
        LocalPreferences.TabModVault tab = localPreferences.getTabModVault();
        ViewHelper.saveColumnLayout(modSearchTableView, tab.getModSearchTableColumnWidths(), tab.getModSearchTableColumnOrder());
        ViewHelper.saveColumnLayout(modVersionTableView, tab.getModVersionTableColumnWidths(), tab.getModVersionTableColumnOrder());
    }
}
