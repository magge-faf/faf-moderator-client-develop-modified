package com.faforever.moderatorclient.ui.domain;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;

import java.util.List;

public class AvatarFX extends AbstractEntityFX {
    private final StringProperty url;
    private final StringProperty tooltip;
    private final ObservableList<AvatarAssignmentFX> assignments;
    private final ObjectProperty<Image> imageProperty = new SimpleObjectProperty<>();

    public AvatarFX() {
        url = new SimpleStringProperty();
        tooltip = new SimpleStringProperty();
        assignments = FXCollections.observableArrayList();
    }

    public String getUrl() {
        return url.get();
    }

    public void setUrl(String url) {
        this.url.set(url);
        this.imageProperty.set(null); // reset cached image without breaking binding
    }

    public StringProperty urlProperty() {
        return url;
    }

    public String getTooltip() {
        return tooltip.get();
    }

    public void setTooltip(String tooltip) {
        this.tooltip.set(tooltip);
    }

    public StringProperty tooltipProperty() {
        return tooltip;
    }

    public List<AvatarAssignmentFX> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<AvatarAssignmentFX> assignmentFXList) {
        assignments.clear();
        if (assignmentFXList != null) {
            assignments.addAll(assignmentFXList);
        }
    }

    public Image getImage() {
        return imageProperty.get();
    }

    public void setImage(Image image) {
        imageProperty.set(image);
    }

    public ObjectProperty<Image> imageProperty() {
        return imageProperty;
    }
}
