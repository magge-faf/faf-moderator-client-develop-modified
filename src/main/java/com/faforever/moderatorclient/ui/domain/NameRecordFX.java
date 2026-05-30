package com.faforever.moderatorclient.ui.domain;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;

import java.time.OffsetDateTime;

public class NameRecordFX extends AbstractEntityFX {
    private final ObjectProperty<OffsetDateTime> changeTime;
    private final ObjectProperty<PlayerFX> player;
    private final StringProperty name;

    public NameRecordFX() {
        changeTime = new SimpleObjectProperty<>();
        player = new SimpleObjectProperty<>();
        name = new SimpleStringProperty();
    }

    public OffsetDateTime getChangeTime() {
        return changeTime.get();
    }

    public void setChangeTime(OffsetDateTime changeTime) {
        this.changeTime.set(changeTime);
    }

    public ObjectProperty<OffsetDateTime> changeTimeProperty() {
        return changeTime;
    }

    public PlayerFX getPlayer() {
        return player.get();
    }

    public void setPlayer(PlayerFX player) {
        this.player.set(player);
    }

    public ObjectProperty<PlayerFX> playerProperty() {
        return player;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }
}
