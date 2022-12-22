package com.faforever.moderatorclient.ui;

import com.faforever.commons.api.dto.TutorialCategory;
import com.faforever.moderatorclient.api.domain.TutorialService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
@RequiredArgsConstructor
public class CategoryAddController implements Controller<Pane> {
    private final TutorialService tutorialService;
    private Runnable onSaveRunnable;

    @FXML
    private TextField categoryField;
    @FXML
    private Label errorLabel;
    @FXML
    private GridPane root;

    @Override
    public Pane getRoot() {
        return root;
    }

    @FXML
    public void initialize() {
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        errorLabel.setVisible(false);
    }

    @FXML
    public void onSave() {
        if (!validate()) {
            return;
        }

        TutorialCategory tutorialCategory = new TutorialCategory();
        tutorialCategory.setCategoryKey(categoryField.getText());

        try {
            if (tutorialService.createCategory(tutorialCategory) == null) {
                showError("Not saved unknown error");
                return;
            }
        } catch (Exception e) {
            showError(MessageFormat.format("Unable to save Tutorial error is: `{0}`", e.getMessage()));
            log.warn("Tutorial not saved", e);
            return;
        }

        closeWindow();
        if (onSaveRunnable != null) {
            onSaveRunnable.run();
        }
    }

    private boolean validate() {
        if (categoryField.getText().isEmpty()) {
            showError("Category Key can not be empty");
            return false;
        }
        return true;
    }

    private void showError(String message) {
        errorLabel.setVisible(true);
        errorLabel.setText(message);
    }

    private void closeWindow() {
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }

    public void setOnSave(Runnable onSaveRunnable) {
        this.onSaveRunnable = onSaveRunnable;
    }
}
