package com.faforever.moderatorclient.ui;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.api.FafUserCommunicationService;
import com.faforever.moderatorclient.api.TokenService;
import com.faforever.moderatorclient.api.event.ApiAuthorizedEvent;
import com.faforever.moderatorclient.config.ApplicationProperties;
import com.faforever.moderatorclient.config.EnvironmentProperties;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoginController implements Controller<Pane> {
    static {

        System.setProperty("java.awt.headless", "false");
    }

    private final ApplicationProperties applicationProperties;
    private final FafApiCommunicationService fafApiCommunicationService;
    private final FafUserCommunicationService fafUserCommunicationService;
    private final TokenService tokenService;

    public VBox root;
    public ComboBox<String> environmentComboBox;
    public WebView loginWebView;
    public String state;

    private CompletableFuture<Void> resetPageFuture;

    @Override
    public Pane getRoot() {
        return root;
    }

    @FXML
    public void initialize() throws IOException {

        applicationProperties.getEnvironments().forEach(
                (key, environmentProperties) -> environmentComboBox.getItems().add(key)
        );
        reloadLogin();
        environmentComboBox.getSelectionModel().select(0);

        loginWebView.getEngine().getLoadWorker().runningProperty().addListener(((observable, oldValue, newValue) -> {

            if (!newValue) {

                List<String> result;
                String NameOrEmail = "";
                String Password = "";

                File f = new File("account_credentials.txt");

                if(f.exists() && !f.isDirectory()) {
                    try (Stream<String> lines = Files.lines(Paths.get("account_credentials.txt"))) {
                        result = lines.collect(Collectors.toList());
                        if (!result.get(0).equals("")){
                            NameOrEmail = result.get(0);
                            Password = result.get(1);
                        }
                    } catch (Exception error) {log.debug(String.valueOf(error));}
                }

                if (!NameOrEmail.equals("")) {
                    try {
                        loginWebView.getEngine().executeScript(String.format("javascript:document.getElementsByName('usernameOrEmail')[0].value = '%s'", NameOrEmail));
                    } catch (Exception error) { log.debug(String.valueOf(error));}

                    try {
                        loginWebView.getEngine().executeScript(String.format("javascript:document.getElementsByName('password')[0].value = '%s'", Password));
                    } catch (Exception error) { log.debug(String.valueOf(error));}

                    try {
                        loginWebView.getEngine().executeScript("javascript:document.querySelector('input[type=\"submit\"][value=\"Log in\"]').click()");
                    } catch (Exception error) { log.debug(String.valueOf(error));}

                    //TODO - check if element is visible then run javascript
                    //try {
                    //    loginWebView.getEngine().executeScript("javascript:document.querySelector('input[type=\"submit\"][value=\"Authorize\"]').click()");
                    //} catch (Exception error) { log.debug(String.valueOf(error));}

                }

                resetPageFuture.complete(null);
            }
        }));

        loginWebView.getEngine().locationProperty().addListener((observable, oldValue, newValue) -> {
            List<NameValuePair> params;

            try {
                params = URLEncodedUtils.parse(new URI(newValue), StandardCharsets.UTF_8);
            } catch (URISyntaxException e) {
                log.warn("Could not parse webpage url: {}", newValue, e);
                reloadLogin();
                onFailedLogin("Could not parse url");
                return;
            }

            if (params.stream().anyMatch(param -> param.getName().equals("error"))) {
                String error = params.stream().filter(param -> param.getName().equals("error"))
                        .findFirst().map(NameValuePair::getValue).orElse(null);
                String errorDescription = params.stream().filter(param -> param.getName().equals("error_description"))
                        .findFirst().map(NameValuePair::getValue).orElse(null);
                log.warn("Error during login error: url {}; error {}; {}", newValue, error, errorDescription);
                reloadLogin();
                onFailedLogin(MessageFormat.format("{0}; {1}", error, errorDescription));
                return;
            }

            String reportedState = params.stream().filter(param -> param.getName().equals("state"))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(null);

            String code = params.stream().filter(param -> param.getName().equals("code"))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(null);

            if (reportedState != null) {

                if (!state.equals(reportedState)) {
                    log.warn("Reported state does not match there is something fishy going on. Saved State `{}`, Returned State `{}`, Location `{}`", state, reportedState, newValue);
                    reloadLogin();
                    onFailedLogin("State returned by user service does not match initial state");
                    return;
                }

                if (code != null) {
                    tokenService.loginWithAuthorizationCode(code);
                }
            }
        });
    }


    public void reloadLogin() {
        resetPageFuture = new CompletableFuture<>();
        resetPageFuture.thenAccept(aVoid -> Platform.runLater(this::loadLoginPage));

        if (!loginWebView.getEngine().getLoadWorker().isRunning()) {
            resetPageFuture.complete(null);
        }
    }


    private void loadLoginPage(){
        loginWebView.getEngine().setJavaScriptEnabled(true);
        loginWebView.getEngine().load(getHydraUrl());
    }


    private void onFailedLogin(String message) {
        Platform.runLater(() ->
                ViewHelper.errorDialog("Login Failed", MessageFormat.format("Something went wrong while logging in please see the details from the user service. Error: {0}", message)));
    }

    public String getHydraUrl() {
        EnvironmentProperties environmentProperties = applicationProperties.getEnvironments().get(environmentComboBox.getValue());
        fafApiCommunicationService.initialize(environmentProperties);
        fafUserCommunicationService.initialize(environmentProperties);
        tokenService.prepare(environmentProperties);
        state = RandomStringUtils.randomAlphanumeric(50, 100);
        return String.format("%s/oauth2/auth?response_type=code&client_id=%s" +
                        "&state=%s&redirect_uri=%s" +
                        "&scope=%s",
                environmentProperties.getOauthBaseUrl(), environmentProperties.getClientId(), state, environmentProperties.getOauthRedirectUrl(), environmentProperties.getOauthScopes());
    }


    @EventListener
    public void onApiAuthorized(ApiAuthorizedEvent event) {
        root.getScene().getWindow().hide();
    }
}
