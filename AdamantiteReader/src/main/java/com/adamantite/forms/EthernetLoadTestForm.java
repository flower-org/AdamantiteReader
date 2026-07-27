package com.adamantite.forms;

import com.adamantite.db.*;
import com.adamantite.dbcodec.FlatBufBlockEncoder;
import com.adamantite.utils.AdamantiteUtils;
import com.flower.fxutils.JavaFxUtils;
import com.flower.fxutils.ModalWindow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.adamantite.db.Block.*;
import static com.adamantite.db.PhraseTemplatesBlock.*;
import static com.adamantite.forms.PhraserDbForm.NEW_BLOCK;
import static com.adamantite.utils.AdamantiteUtils.*;
import static com.flower.fxutils.JavaFxUtils.YesNo.YES;
import static com.google.common.base.Preconditions.checkNotNull;

public class EthernetLoadTestForm extends AnchorPane {
    final static Logger LOGGER = LoggerFactory.getLogger(EthernetLoadTestForm.class);

    public static class PhraseTemplateWord {
        final WordTemplate wordTemplate;
        final @Nullable Short wordTemplateOrdinal;

        PhraseTemplateWord(WordTemplate wordTemplate, @Nullable Short wordTemplateOrdinal) {
            this.wordTemplate = wordTemplate;
            this.wordTemplateOrdinal = wordTemplateOrdinal;
        }

        public int getId() { return wordTemplate.getId(); }
        public String getName() { return wordTemplate.getName(); }
        public @Nullable Short getOrdinal() { return wordTemplateOrdinal; }
    }

    @Nullable PhraseTemplate phraseTemplate = null;

    @FXML @Nullable TextField phraseTemplateIdTextField;
    @FXML @Nullable TextField phraseTemplateNameTextField;
    @FXML @Nullable TableView<PhraseTemplateWord> phraseTemplateWordsTableView;

    @Nullable WordTemplate oldWordTemplate = null;

    @FXML @Nullable TextField wordTemplateIdTextField;
    @FXML @Nullable TextField wordTemplateNameTextField;
    @FXML @Nullable ComboBox<String> wordTemplateIconComboBox;
    @FXML @Nullable TextField wordTemplateMinLengthTextField;

    @FXML @Nullable CheckBox wordTemplateIsGenerateableCheckBox;
    @FXML @Nullable CheckBox wordTemplateIsTypeableCheckBox;
    @FXML @Nullable CheckBox wordTemplateIsViewableCheckBox;
    @FXML @Nullable CheckBox wordTemplateIsUserEditableCheckBox;

    @FXML @Nullable TableView<SymbolSetsBlock.SymbolSet> wordTemplateSymbolSetsTableView;
    ObservableList<SymbolSetsBlock.SymbolSet> wordTemplateSymbolSets;

    @FXML @Nullable Button addWordTemplateSymbolSetButton;
    @FXML @Nullable Button removeWordTemplateSymbolSetButton;
    @FXML @Nullable TextField entropyTextField;

    @Nullable Stage stage;

    int nextWordTemplateId = 0;
    int nextPhraseTemplateId = 0;

    public EthernetLoadTestForm() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("EthernetLoadTestForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        checkNotNull(wordTemplateMinLengthTextField).setTextFormatter(new TextFormatter<>(change -> {
                String newText = change.getControlNewText();
                if (newText.length() <= 4 && newText.matches("[0-9]*")) {
                    change.setText(change.getText().toLowerCase());
                    return change;
                }
                return null;
            }
        ));
        this.wordTemplateSymbolSets = FXCollections.observableArrayList();

        checkNotNull(wordTemplateMinLengthTextField).textProperty().set("16");

        updateBlockSize();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    void updateBlockSize() {
    }

    public boolean phraseTemplateChanged() {
        if (phraseTemplate != null) {
            try {
                PhraseTemplate newPhraseTemplate = formPhraseTemplate();
                return !newPhraseTemplate.equals(phraseTemplate);
            } catch (Exception e) {
                LOGGER.error("phraseTemplateChanged error", e);
            }
        }
        return false;
    }

    public boolean wordTemplateChanged() {
        if (oldWordTemplate != null) {
            try {
                WordTemplate newWordTemplate = formWordTemplate();
                return !newWordTemplate.equals(oldWordTemplate);
            } catch (Exception e) {
                LOGGER.error("wordTemplateChanged error", e);
            }
        }
        return false;
    }

    public void saveToDb() {
        if (phraseTemplateChanged()) {
            if (YES == JavaFxUtils.showYesNoDialog("PhraseTemplate was changed but not updated, apply changes?")) {
                addUpdatePhraseTemplate();
            }
        }

        if (wordTemplateChanged()) {
            if (YES == JavaFxUtils.showYesNoDialog("WordTemplate was changed but not updated, apply changes?")) {
                addUpdateWordTemplate();
            }
        }
    }

    public void newWordTemplate() {
        oldWordTemplate = null;

        //id
        checkNotNull(wordTemplateIdTextField).textProperty().set("[NEW WORD TEMPLATE]");
        //name
        checkNotNull(wordTemplateNameTextField).textProperty().set("");
        //icon
        checkNotNull(wordTemplateIconComboBox).getSelectionModel().select(0);
        //minLength
        checkNotNull(wordTemplateMinLengthTextField).textProperty().set("10");
        //permissions
        checkNotNull(wordTemplateIsGenerateableCheckBox).selectedProperty().addListener((observable, oldValue, newValue) -> {
            boolean isGenerateable = newValue;
            disableSymbolSets(!isGenerateable);
        });
        checkNotNull(wordTemplateIsGenerateableCheckBox).selectedProperty().set(true);
        checkNotNull(wordTemplateIsTypeableCheckBox).selectedProperty().set(true);
        checkNotNull(wordTemplateIsViewableCheckBox).selectedProperty().set(false);
        checkNotNull(wordTemplateIsUserEditableCheckBox).selectedProperty().set(true);

        //symbol sets
        wordTemplateSymbolSets = FXCollections.observableArrayList();
        checkNotNull(wordTemplateSymbolSetsTableView).itemsProperty().set(wordTemplateSymbolSets);
    }

    public void removeWordTemplate() {
        updateBlockSize();
    }

    void disableSymbolSets(boolean disable) {;
        checkNotNull(addWordTemplateSymbolSetButton).setDisable(disable);
        checkNotNull(removeWordTemplateSymbolSetButton).setDisable(disable);
        checkNotNull(wordTemplateSymbolSetsTableView).setDisable(disable);
    }

    public void showWordTemplate(WordTemplate wordTemplate) {
        //id
        checkNotNull(wordTemplateIdTextField).textProperty().set(Integer.toString(wordTemplate.wordTemplateId()));
        //name
        checkNotNull(wordTemplateNameTextField).textProperty().set(wordTemplate.wordTemplateName());
        //icon
        checkNotNull(wordTemplateIconComboBox).getSelectionModel().select(wordTemplate.icon().toString());
        //minLength
        checkNotNull(wordTemplateMinLengthTextField).textProperty().set(Integer.toString(wordTemplate.minLength()));
        //permissions
        //TODO: DRY
        checkNotNull(wordTemplateIsGenerateableCheckBox).selectedProperty().addListener((observable, oldValue, newValue) -> {
            boolean isGenerateable = newValue;
            disableSymbolSets(!isGenerateable);
        });
        byte permissions = wordTemplate.permissions();
        boolean isGenerateable = isGenerateable(permissions);
        disableSymbolSets(!isGenerateable);
        checkNotNull(wordTemplateIsGenerateableCheckBox).selectedProperty().set(isGenerateable);
        checkNotNull(wordTemplateIsTypeableCheckBox).selectedProperty().set(isTypeable(permissions));
        checkNotNull(wordTemplateIsViewableCheckBox).selectedProperty().set(isViewable(permissions));
        checkNotNull(wordTemplateIsUserEditableCheckBox).selectedProperty().set(isUserEditable(permissions));

        //symbol sets
        wordTemplateSymbolSets = FXCollections.observableArrayList();

        checkNotNull(wordTemplateSymbolSetsTableView).itemsProperty().set(wordTemplateSymbolSets);
    }

    public void showPhraseTemplate(PhraseTemplate phraseTemplate) {
        //id
        checkNotNull(phraseTemplateIdTextField).textProperty().set(Integer.toString(phraseTemplate.phraseTemplateId()));
        //name
        checkNotNull(phraseTemplateNameTextField).textProperty().set(phraseTemplate.phraseTemplateName());

        Map<Integer, WordTemplate> wordTemplateMap = new HashMap<>();

        for (WordTemplateRef wordTemplateRef : phraseTemplate.wordTemplateRefs()) {
            WordTemplate wTemplate = wordTemplateMap.get(wordTemplateRef.wordTemplateId());
            if (wTemplate == null) {
                wTemplate = ImmutableWordTemplate.builder()
                        .wordTemplateId(wordTemplateRef.wordTemplateId())
                        .wordTemplateName("WORD TEMPLATE NOT FOUND")
                        .addSymbolSetIds()
                        .permissions((byte)0)
                        .icon(Icon.X)
                        .minLength(0)
                        .maxLength(0)
                        .build();
            }
        }
    }

    public void newPhraseTemplate() {
        phraseTemplate = null;

        checkNotNull(phraseTemplateIdTextField).textProperty().set("[NEW PHRASE TEMPLATE]");
        checkNotNull(phraseTemplateNameTextField).textProperty().set("");
    }

    public void addSymbolSet() {
        try {
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error picking Symbol Set: " + e, ButtonType.OK);
            LOGGER.error("Error picking Symbol Set: ", e);
            alert.showAndWait();
        }
    }

    public void removeSymbolSet() {
        try {
            SymbolSetsBlock.SymbolSet symbolSet = checkNotNull(wordTemplateSymbolSetsTableView).getSelectionModel().getSelectedItem();
            if (symbolSet != null) {
                wordTemplateSymbolSets.remove(symbolSet);
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error removing Symbol Set: " + e, ButtonType.OK);
            LOGGER.error("Error removing Symbol Set: ", e);
            alert.showAndWait();
        }
    }

    public void addUpdateWordTemplate() {
        try {
            WordTemplate newWordTemplate = formWordTemplate();
            addWordTemplate(newWordTemplate, true);

            updateBlockSize();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            LOGGER.error("Error in addUpdateWordTemplate: ", e);
            alert.showAndWait();
        }
    }

    void addWordTemplate(WordTemplate newWordTemplate, boolean selectNewTemplate) {
        int index = -1;

        nextWordTemplateId = Math.max(nextWordTemplateId, newWordTemplate.wordTemplateId());

        if (selectNewTemplate) {
            oldWordTemplate = newWordTemplate;
        }
    }

    WordTemplate formWordTemplate() {
        int wordTemplateId;
        if (oldWordTemplate == null) {
            wordTemplateId = nextWordTemplateId + 1;
        } else {
            wordTemplateId = oldWordTemplate.wordTemplateId();
        }

        byte permissions = getWordPermissions();
        Icon icon = Icon.valueOf(checkNotNull(wordTemplateIconComboBox).getSelectionModel().getSelectedItem());
        int minLength = Integer.parseInt(checkNotNull(wordTemplateMinLengthTextField).textProperty().get());
        String wordTemplateName = checkNotNull(wordTemplateNameTextField).getText();
        if (StringUtils.isBlank(wordTemplateName)) {
            throw new RuntimeException("Word template name is empty");
        }

        List<Integer> symbolSetIds = new ArrayList<>();
        for (SymbolSetsBlock.SymbolSet symbolSet : wordTemplateSymbolSets) {
            symbolSetIds.add(symbolSet.symbolSetId());
        }
        if (isGenerateable(permissions) && symbolSetIds.isEmpty()) {
            throw new RuntimeException("Generateable words should have symbol sets attached.");
        }

        return ImmutableWordTemplate.builder()
                    .wordTemplateId(wordTemplateId)
                    .permissions(permissions)
                    .icon(icon)
                    .minLength(minLength)
                    .wordTemplateName(wordTemplateName)
                    .symbolSetIds(symbolSetIds)
                .build();
    }

    byte getWordPermissions() {
        boolean generateableOrEditable = false;
        boolean typeableOrViewable = false;
        int getWordPermissions = 0;
        if (checkNotNull(wordTemplateIsGenerateableCheckBox).selectedProperty().get()) {
            getWordPermissions = getWordPermissions | GENERATEABLE;
            generateableOrEditable = true;
        }
        if (checkNotNull(wordTemplateIsTypeableCheckBox).selectedProperty().get()) {
            getWordPermissions = getWordPermissions | TYPEABLE;
            typeableOrViewable = true;
        }
        if (checkNotNull(wordTemplateIsViewableCheckBox).selectedProperty().get()) {
            getWordPermissions = getWordPermissions | VIEWABLE;
            typeableOrViewable = true;
        }
        if (checkNotNull(wordTemplateIsUserEditableCheckBox).selectedProperty().get()) {
            getWordPermissions = getWordPermissions | USER_EDITABLE;
            generateableOrEditable = true;
        }

        if (!generateableOrEditable) {
            throw new RuntimeException("Word should be either Generateable or UserEditable or both");
        }
        if (!typeableOrViewable) {
            throw new RuntimeException("Word should be either Typeable or Viewable or both");
        }

        return (byte)getWordPermissions;
    }

    public void addPhraseTemplateWord() {
        try {
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error picking Word Template: " + e, ButtonType.OK);
            LOGGER.error("Error picking Word Template: ", e);
            alert.showAndWait();
        }
    }

    public void removePhraseTemplateWord() {
        int selectedIndex =
                checkNotNull(phraseTemplateWordsTableView).getSelectionModel().getSelectedIndex();
    }

    public void addUpdatePhraseTemplate() {
        try {
            PhraseTemplate newPhraseTemplate = formPhraseTemplate();
            addPhraseTemplate(newPhraseTemplate, true);

            updateBlockSize();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            LOGGER.error("addUpdatePhraseTemplate: ", e);
            alert.showAndWait();
        }
    }

    PhraseTemplate formPhraseTemplate() {
        int phraseTemplateId;
        if (phraseTemplate == null) {
            phraseTemplateId = nextPhraseTemplateId + 1;
        } else {
            phraseTemplateId = phraseTemplate.phraseTemplateId();
        }

        String phraseTemplateName = checkNotNull(phraseTemplateNameTextField).getText();
        if (StringUtils.isBlank(phraseTemplateName)) {
            throw new RuntimeException("Phrase template name is empty");
        }

        return ImmutablePhraseTemplate.builder()
                    .phraseTemplateId(phraseTemplateId)
                    .phraseTemplateName(phraseTemplateName)
                .build();
    }

    void addPhraseTemplate(PhraseTemplate newPhraseTemplate, boolean selectPhraseTemplate) {
        nextPhraseTemplateId = Math.max(nextPhraseTemplateId, newPhraseTemplate.phraseTemplateId());

        if (selectPhraseTemplate) {
            phraseTemplate = newPhraseTemplate;
        }
    }

    // ----------------------------------------------------------------------

}
