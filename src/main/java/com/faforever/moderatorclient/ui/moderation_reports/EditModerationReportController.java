package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.commons.api.dto.ModerationReport;
import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.ui.Controller;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.faforever.moderatorclient.ui.MainController.CONFIGURATION_FOLDER;

@Component
@RequiredArgsConstructor
public class EditModerationReportController implements Controller<Pane> {
	private List<ModerationReportFX> selectedReports = new ArrayList<>();
	private final ModerationReportService moderationReportService;
	public TextArea privateNoteTextArea;
	public TextArea publicNoteTextArea;
	public ChoiceBox<ModerationReportStatus> statusChoiceBox;
	public Pane root;
    public CheckBox autoApplyTemplateAndSaveCheckBox;
	@FXML
	public VBox dynamicButtonsContainer;
	@Setter
	private Runnable onSaveRunnable = () -> {};
    @FXML
	public void initialize() throws IOException {
		try {
			loadButtonsFromJson(CONFIGURATION_FOLDER + File.separator + "templatesFinishReports.json");
		} catch (IOException e) {
			throw new IOException("Failed to initialize Buttons" + e);
		}

		loadAutoApplyTemplateAndSaveProperties();
		statusChoiceBox.setItems(FXCollections.observableArrayList(ModerationReportStatus.values()));
	}

	Properties properties = new Properties();
	String PROPERTIES_FILE = CONFIGURATION_FOLDER + File.separator + "config.properties";

	public void onSaveAutoApplyTemplateAndSaveCheckBox() throws IOException {
		File propertiesFile = new File(PROPERTIES_FILE);

		if (propertiesFile.exists()) {
			try (FileInputStream in = new FileInputStream(propertiesFile)) {
				properties.load(in);
			}
		}

		properties.setProperty("autoApplyTemplateAndSaveCheckBox", Boolean.toString(autoApplyTemplateAndSaveCheckBox.isSelected()));

		try (FileOutputStream out = new FileOutputStream(propertiesFile)) {
			properties.store(out, null );
		} catch (IOException e) {
			throw new IOException("Failed to save properties file: " + propertiesFile.getAbsolutePath(), e);
		}
	}

	private void loadAutoApplyTemplateAndSaveProperties() throws IOException {
		File propertiesFile = new File(PROPERTIES_FILE);
		if (propertiesFile.exists()) {
			try (FileInputStream in = new FileInputStream(propertiesFile)) {
				Properties properties = new Properties();
				properties.load(in);
				autoApplyTemplateAndSaveCheckBox.setSelected(Boolean.parseBoolean(properties.getProperty("autoApplyTemplateAndSaveCheckBox", "false")));
			} catch (IOException e) {
				throw new IOException("Failed to load properties file: " + propertiesFile.getAbsolutePath(), e);
			}
		}
	}

	public void onSave() throws IOException {
		for (ModerationReportFX report : selectedReports) {
			ModerationReport updateModerationReport = new ModerationReport();
			updateModerationReport.setId(report.getId());
			updateModerationReport.setReportStatus(statusChoiceBox.getSelectionModel().getSelectedItem());
			updateModerationReport.setModeratorPrivateNote(privateNoteTextArea.getText());
			updateModerationReport.setModeratorNotice(publicNoteTextArea.getText());
			moderationReportService.patchReport(updateModerationReport);
			report.setReportStatus(statusChoiceBox.getSelectionModel().getSelectedItem());
			report.setModeratorPrivateNote(privateNoteTextArea.getText());
			report.setModeratorNotice(publicNoteTextArea.getText());
		}
		onSaveAutoApplyTemplateAndSaveCheckBox();
		onSaveRunnable.run();
		close();
	}

    public void setModerationReportFx(ModerationReportFX moderationReportFx) {
		statusChoiceBox.getSelectionModel().select(moderationReportFx.getReportStatus());
		privateNoteTextArea.setText(moderationReportFx.getModeratorPrivateNote());
		publicNoteTextArea.setText(moderationReportFx.getModeratorNotice());
	}

	@Override
	public Pane getRoot() {
		return root;
	}

	public void close() {
		Stage stage = (Stage) root.getScene().getWindow();
		stage.close();
	}

	public void loadButtonsFromJson(String filePath) throws IOException {
		String content = new String(Files.readAllBytes(Paths.get(filePath)));
		JSONObject json = new JSONObject(content);
		JSONArray templates = json.getJSONArray("templatesEditReports");

		for (int i = 0; i < templates.length(); i++) {
			JSONObject template = templates.getJSONObject(i);
			String buttonName = template.getString("buttonName");
			String descriptionPublicNote = template.getString("descriptionPublicNote");
			String setReportStatusTo = template.getString("setReportStatusTo");

			Button button = new Button(buttonName);
			button.setOnAction(e -> {
				try {
					handleButtonAction(setReportStatusTo, descriptionPublicNote);
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			});

			Tooltip tooltip = new Tooltip(descriptionPublicNote);
			PauseTransition pause = new PauseTransition(Duration.millis(0));

			button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> pause.playFromStart());
			button.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
				pause.stop();
				tooltip.hide();
			});

			pause.setOnFinished(e -> tooltip.show(button, button.getScene().getWindow().getX(), button.getScene().getWindow().getY()));

			dynamicButtonsContainer.getChildren().add(button);
		}
	}

	private void handleButtonAction(String setReportStatusTo, String descriptionPublicNote) throws IOException {
		publicNoteTextArea.setText(descriptionPublicNote);
		statusChoiceBox.getSelectionModel().select(ModerationReportStatus.valueOf(setReportStatusTo));
		if (autoApplyTemplateAndSaveCheckBox.isSelected()) {
			onSave();
		}
	}

	public void setSelectedReports(List<ModerationReportFX> reports) {
		this.selectedReports = reports;
		if (!selectedReports.isEmpty()) {
			setModerationReportFx(selectedReports.getFirst());
		}
	}

}
