package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Async;
import com.quizarena.client.ui.Navigator;
import com.quizarena.client.ui.Ui;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Historique des parties : date, catégorie, rang, score et durée. */
public class HistoryController implements Navigator.Screen {

    @FXML private TableView<Dto.HistoryEntry> historyTable;
    @FXML private TableColumn<Dto.HistoryEntry, String> dateColumn;
    @FXML private TableColumn<Dto.HistoryEntry, String> categoryColumn;
    @FXML private TableColumn<Dto.HistoryEntry, String> rankColumn;
    @FXML private TableColumn<Dto.HistoryEntry, Number> scoreColumn;
    @FXML private TableColumn<Dto.HistoryEntry, Number> playersColumn;
    @FXML private TableColumn<Dto.HistoryEntry, String> durationColumn;
    @FXML private Label statusLabel;
    /**
 * Initialise le tableau d'historique et charge les parties terminées
 * depuis l'API afin de les afficher à l'utilisateur.
 */

    @FXML
    public void initialize() {
        historyTable.setPlaceholder(new Label("Aucune partie terminée pour le moment."));
        dateColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(Ui.formatDate(cell.getValue().endedAt())));
        categoryColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().categoryName()));
        rankColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(Ui.rankLabel(cell.getValue().finalRank())
                        + " / " + cell.getValue().playersCount()));
        scoreColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().score()));
        playersColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().playersCount()));
        durationColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(Ui.formatDuration(cell.getValue().durationSeconds())));

        statusLabel.setText("Chargement…");
        Async.run(() -> AppContext.api().history(), entries -> {
            historyTable.getItems().setAll(entries);
            statusLabel.setText(entries.isEmpty() ? "" : entries.size() + " partie(s) terminée(s)");
        }, e -> statusLabel.setText(e.getMessage()));
    }

    @FXML
    private void onBack() {
        Navigator.go("home");
    }
}
