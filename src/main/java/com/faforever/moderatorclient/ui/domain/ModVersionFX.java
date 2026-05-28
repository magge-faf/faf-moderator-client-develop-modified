package com.faforever.moderatorclient.ui.domain;

import com.faforever.commons.api.dto.Mod;
import com.faforever.commons.api.dto.ModType;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.net.URI;

public class ModVersionFX extends AbstractEntityFX {
    private final StringProperty uid;
    private final ObjectProperty<ModType> type;
    private final StringProperty description;
    private final ObjectProperty<ComparableVersion> version;
    private final StringProperty filename;
    private final StringProperty icon;
    private final BooleanProperty ranked;
    private final BooleanProperty hidden;
    private final ObjectProperty<URI> thumbnailUrl;
    private final ObjectProperty<URI> downloadUrl;
    private final ObjectProperty<Mod> mod;


    public ModVersionFX() {
        uid = new SimpleStringProperty();
        type = new SimpleObjectProperty<>();
        description = new SimpleStringProperty();
        version = new SimpleObjectProperty<>();
        filename = new SimpleStringProperty();
        icon = new SimpleStringProperty();
        ranked = new SimpleBooleanProperty();
        hidden = new SimpleBooleanProperty();
        thumbnailUrl = new SimpleObjectProperty<>();
        downloadUrl = new SimpleObjectProperty<>();
        mod = new SimpleObjectProperty<>();
    }

    public String getUid() {
        return uid.get();
    }

    public void setUid(String uid) {
        this.uid.set(uid);
    }

    public StringProperty uidProperty() {
        return uid;
    }

    public ModType getType() {
        return type.get();
    }

    public void setType(ModType type) {
        this.type.set(type);
    }

    public ObjectProperty<ModType> typeProperty() {
        return type;
    }

    public String getDescription() {
        return description.get();
    }

    public void setDescription(String description) {
        this.description.set(description);
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public ComparableVersion getVersion() {
        return version.get();
    }

    public void setVersion(ComparableVersion version) {
        this.version.set(version);
    }

    public ObjectProperty<ComparableVersion> versionProperty() {
        return version;
    }

    public String getFilename() {
        return filename.get();
    }

    public void setFilename(String filename) {
        this.filename.set(filename);
    }

    public StringProperty filenameProperty() {
        return filename;
    }

    public String getIcon() {
        return icon.get();
    }

    public void setIcon(String icon) {
        this.icon.set(icon);
    }

    public StringProperty iconProperty() {
        return icon;
    }

    public boolean isRanked() {
        return ranked.get();
    }

    public void setRanked(boolean ranked) {
        this.ranked.set(ranked);
    }

    public BooleanProperty rankedProperty() {
        return ranked;
    }

    public boolean isHidden() {
        return hidden.get();
    }

    public void setHidden(boolean hidden) {
        this.hidden.set(hidden);
    }

    public BooleanProperty hiddenProperty() {
        return hidden;
    }

    public URI getThumbnailUrl() {
        return thumbnailUrl.get();
    }

    public void setThumbnailUrl(URI thumbnailUrl) {
        this.thumbnailUrl.set(thumbnailUrl);
    }

    public ObjectProperty<URI> thumbnailUrlProperty() {
        return thumbnailUrl;
    }

    public URI getDownloadUrl() {
        return downloadUrl.get();
    }

    public void setDownloadUrl(URI downloadUrl) {
        this.downloadUrl.set(downloadUrl);
    }

    public ObjectProperty<URI> downloadUrlProperty() {
        return downloadUrl;
    }

    public Mod getMod() {
        return mod.get();
    }

    public void setMod(Mod mod) {
        this.mod.set(mod);
    }

    public ObjectProperty<Mod> modProperty() {
        return mod;
    }
}
