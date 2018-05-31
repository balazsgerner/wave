package application;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application implements Initializable {

  private static final int CANVAS_WIDTH = 800;

  private static final int CANVAS_HEIGHT = 600;

  private static final int THRESHOLD = -80;

  private static final int NUMBER_OF_BANDS = 64;

  private GraphicsContext ctx;

  private MediaPlayer mp;

  private ObservableList<Float> spectrum;

  private ObservableList<Float> phases;

  private List<Color> colors;

  private String artistName;

  private String title;

  private SimpleObjectProperty<File> selectedFileProperty;

  private SimpleBooleanProperty musicPlayingProperty;

  private ObjectProperty<Number> numberOfBandsProperty;

  private ObjectProperty<Number> musicProgressProperty;

  private ObjectProperty<Number> thresholdProperty;

  private SimpleBooleanProperty fileDirtyProperty;

  @FXML
  private Parent root;

  @FXML
  private Label titleLabel;

  @FXML
  private Label timeRemainingLabel;

  @FXML
  private Label currentTimeLabel;

  @FXML
  private Canvas canvas;

  @FXML
  private MenuItem openFileMenu;

  @FXML
  private Button playButton;

  @FXML
  private Button btnSelectSong;

  @FXML
  private ComboBox<Number> cmbNumberOfBands;

  @FXML
  private ComboBox<Number> cmbThreshold;

  @FXML
  private ProgressBar musicProgress;

  @Override
  public void start(Stage primaryStage) {
    try {
      root = FXMLLoader.load(getClass().getResource("/resources/view/main.fxml"));
      Scene scene = new Scene(root, 800, 790);
      primaryStage.setScene(scene);
      primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/resources/icon.png")));
      scene.getStylesheets().add("https://fonts.googleapis.com/css?family=Play");
      primaryStage.setTitle("wave");
      primaryStage.sizeToScene();
      root.getStylesheets().add(getClass().getResource("/resources/style.css").toExternalForm());
      primaryStage.setResizable(false);
      primaryStage.show();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @FXML
  private void openFile() {
    FileChooser chooser = new FileChooser();
    File selectedFile = chooser.showOpenDialog(root.getScene().getWindow());
    if (selectedFile != null) {
      if (musicPlayingProperty.get()) {
        mp.stop();
      }
      selectedFileProperty.set(selectedFile);
      fileDirtyProperty.set(true);
      mp = new MediaPlayer(new Media(selectedFile.toURI().toString()));
      mp.setOnEndOfMedia(() -> mp.stop());
      musicPlayingProperty.bind(mp.statusProperty().isEqualTo(Bindings.createObjectBinding(() -> Status.PLAYING, mp.statusProperty())));

      mp.audioSpectrumNumBandsProperty().bindBidirectional(numberOfBandsProperty);

      musicProgressProperty.bind(Bindings.createDoubleBinding(() -> {
        if (mp.getCurrentTime() == null || mp.getTotalDuration() == null) {
          return 0.0;
        }
        return mp.getCurrentTime().toMillis() / mp.getTotalDuration().toMillis();
      }, mp.currentTimeProperty(), mp.totalDurationProperty()));

      currentTimeLabel.textProperty().bind(Bindings.createStringBinding(() -> {
        double totalSeconds = mp.getCurrentTime().toSeconds();
        int minutes = (int) totalSeconds / 60;
        int rest = (int) totalSeconds - (minutes * 60);
        return String.format("%02d:%02d", minutes, rest);
      }, mp.currentTimeProperty(), mp.totalDurationProperty(), musicPlayingProperty));

      timeRemainingLabel.textProperty().bind(Bindings.createStringBinding(() -> {
        Duration currentTime = mp.getCurrentTime();
        Duration totalDuration = mp.getTotalDuration();
        if (currentTime == null || totalDuration == null) {
          return "-00:00";
        }
        double currentSeconds = currentTime.toSeconds();
        double totalSeconds = totalDuration.toSeconds();
        int diff = (int) totalSeconds - (int) currentSeconds;
        int minutes = (int) diff / 60;
        int rest = (int) diff - (minutes * 60);
        return String.format("-%02d:%02d", minutes, rest);
      }, mp.currentTimeProperty(), mp.totalDurationProperty(), musicPlayingProperty));

      musicProgress.progressProperty().bind(musicProgressProperty);
      mp.audioSpectrumThresholdProperty().bind(thresholdProperty);
      mp.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
        List<Float> resultMagnitude = new ArrayList<>();
        List<Float> resultPhases = new ArrayList<>();
        for (int i = 0; i < magnitudes.length; i++) {
          resultMagnitude.add(-Float.valueOf(magnitudes[i]));
          resultPhases.add(Float.valueOf(phases[i]));
        }
        spectrum.setAll(resultMagnitude);
        this.phases.setAll(resultPhases);
      });

    }
  }

  private void initColors() {
    final float frequency = 0.3f;
    colors = new ArrayList<>();
    for (int i = 0; i < numberOfBandsProperty.get().intValue(); i++) {
      double red = Math.sin(frequency * i + 0) * 127 / 255f + 128 / 255f;
      double green = Math.sin(frequency * i + 2) * 127 / 255f + 128 / 255f;
      double blue = Math.sin(frequency * i + 4) * 127 / 255f + 128 / 255f;
      colors.add(new Color(red, green, blue, 1));
    }
  }

  private void initMusicData() {
    final File value = selectedFileProperty.get();
    if (value == null || !fileDirtyProperty.get()) {
      return;
    }
    AudioFile audioFile;
    Tag tag = null;
    try {
      audioFile = AudioFileIO.read(value);
      tag = audioFile.getTag();
    } catch (Exception e) {
      e.printStackTrace();
    }
    artistName = tag.getFirst(FieldKey.ARTIST);
    title = tag.getFirst(FieldKey.TITLE);
    fileDirtyProperty.set(false);
  }

  private void repaintCanvas() {
    ctx.setFill(Color.BLACK);
    ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

    if (spectrum == null) {
      return;
    }

    final float fullLineWidth = (float) CANVAS_WIDTH / numberOfBandsProperty.get().intValue();
    final float lineWidth = fullLineWidth * 0.8f;
    final float lineOffset = (fullLineWidth - lineWidth) / 2;

    final float padding = 30;
    final float textHeight = 45;
    final float spectrumAreaHeight = (CANVAS_HEIGHT - 2 * padding - textHeight) * 0.85f;
    final float phaseAreaHeight = (CANVAS_HEIGHT - 2 * padding - textHeight) * 0.15f;
    final float scaleSpectrum = -spectrumAreaHeight / thresholdProperty.get().floatValue();

    for (int i = 0; i < numberOfBandsProperty.get().intValue(); i++) {
      ctx.setFill(colors.get(i));
      float value = spectrum.isEmpty() ? -thresholdProperty.get().floatValue() : spectrum.get(i);
      float lineHeight = value * scaleSpectrum;
      ctx.fillRoundRect(0 + i * fullLineWidth + lineOffset, padding + spectrumAreaHeight - lineHeight, lineWidth, lineHeight, 5, 5);
    }

    ctx.setFill(Color.YELLOW);
    ctx.setFont(Font.font("Play", 30));
    ctx.setTextAlign(TextAlignment.RIGHT);
    ctx.fillText("wave", CANVAS_WIDTH - 10, padding + spectrumAreaHeight + 30);

    final float scalePhase = (float) (phaseAreaHeight / Math.PI);
    for (int i = 0; i < numberOfBandsProperty.get().intValue(); i++) {
      Color col = colors.get(i);
      Color fadedCol = col.deriveColor(col.getHue(), col.getSaturation(), col.getBrightness(), 0.5);
      ctx.setFill(fadedCol);
      float value = phases.isEmpty() ? (float) Math.PI : phases.get(i);
      value = value * scalePhase;
      ctx.fillRoundRect((CANVAS_WIDTH - lineWidth) - (i * fullLineWidth + lineOffset), CANVAS_HEIGHT - padding - phaseAreaHeight, lineWidth, value, 5,
          5);
    }
  }

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    openFileMenu.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
    final ImageView playImg = new ImageView(new Image(getClass().getResourceAsStream("/resources/play.png")));
    final ImageView pauseImg = new ImageView(new Image(getClass().getResourceAsStream("/resources/pause.png")));
    final ImageView musicImg = new ImageView(new Image(getClass().getResourceAsStream("/resources/music.png"), 28, 28, true, true));
    btnSelectSong.setGraphic(musicImg);
    ctx = canvas.getGraphicsContext2D();
    spectrum = FXCollections.observableArrayList();
    phases = FXCollections.observableArrayList();
    initProperties(playImg, pauseImg);
    initColors();
    initMusicData();
    initTimeline();
  }

  private void initTimeline() {
    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(10), event -> repaintCanvas()));
    timeline.setCycleCount(Animation.INDEFINITE);
    timeline.play();
  }

  private void initProperties(final ImageView playImg, final ImageView pauseImg) {
    fileDirtyProperty = new SimpleBooleanProperty(false);
    selectedFileProperty = new SimpleObjectProperty<File>();
    musicPlayingProperty = new SimpleBooleanProperty(false);
    musicProgressProperty = new SimpleObjectProperty<>(0.0);
    thresholdProperty = new SimpleObjectProperty<>(THRESHOLD);
    numberOfBandsProperty = new SimpleObjectProperty<>(NUMBER_OF_BANDS);
    numberOfBandsProperty.addListener(e -> initColors());

    playButton.disableProperty().bind(Bindings.isNull(selectedFileProperty));
    playButton.textProperty().bind(Bindings.when(musicPlayingProperty).then("Pause").otherwise("Play"));
    playButton.graphicProperty().bind(Bindings.when(musicPlayingProperty).then(pauseImg).otherwise(playImg));
    playButton.setOnAction(e -> {
      if (musicPlayingProperty.get()) {
        mp.pause();
      } else {
        mp.play();
      }
    });

    fileDirtyProperty.addListener(e -> {
      initMusicData();
      if (artistName != null && title != null) {
        titleLabel.setText(String.join(" - ", artistName, title));
      }
    });

    cmbNumberOfBands.getItems().setAll(8, 12, 24, 32, 50, 64, 80, 128);
    cmbNumberOfBands.valueProperty().bindBidirectional(numberOfBandsProperty);
    Tooltip tooltip = new Tooltip("Threshold value in dB");
    cmbThreshold.setTooltip(tooltip);
    cmbThreshold.getItems().setAll(-60, -80, -90, -100, -110, -120);
    cmbThreshold.valueProperty().bindBidirectional(thresholdProperty);
  }
}
