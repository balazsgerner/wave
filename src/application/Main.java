package application;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.LogManager;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.StringBinding;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.media.AudioSpectrumListener;
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

  private static final int ARC = 5;

  private static final int CANVAS_WIDTH = 800;

  private static final int CANVAS_HEIGHT = 600;

  private static final int THRESHOLD = -100;

  private static final int NUMBER_OF_BANDS = 32;

  private GraphicsContext ctx;

  private GraphicsContext bgctx;

  private MediaPlayer mp;

  private volatile float[] spectrum;

  private volatile float[] phases;

  private ObservableList<Color> colors;

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
  private Canvas bgCanvas;

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

  private FileChooser chooser;

  @Override
  public void start(Stage primaryStage) {
    try {
      root = FXMLLoader.load(getClass().getResource("/resources/view/main.fxml"));
      Scene scene = new Scene(root, 800, 800);
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
    File selectedFile = chooser.showOpenDialog(root.getScene().getWindow());
    if (selectedFile != null) {
      if (musicPlayingProperty.get()) {
        mp.stop();
      }
      selectedFileProperty.set(selectedFile);
      fileDirtyProperty.set(true);
      mp = new MediaPlayer(new Media(selectedFile.toURI().toString()));
      mp.setOnEndOfMedia(() -> resetToStart());
      musicPlayingProperty.bind(mp.statusProperty().isEqualTo(Bindings.createObjectBinding(() -> Status.PLAYING)));
      mp.audioSpectrumNumBandsProperty().bindBidirectional(numberOfBandsProperty);

      DoubleBinding musicProgressValue = getMusicProgressValueProperty();
      musicProgressProperty.bind(musicProgressValue);
      StringBinding currentTimeProperty = getCurrentTimeProperty();
      currentTimeLabel.textProperty().bind(currentTimeProperty);
      StringBinding timeRemainingProperty = getTimeRemainingProperty();
      timeRemainingLabel.textProperty().bind(timeRemainingProperty);

      musicProgress.progressProperty().bind(musicProgressProperty);
      mp.audioSpectrumThresholdProperty().bind(thresholdProperty);
      mp.setAudioSpectrumListener(createSpectrumListener());
      playButton.requestFocus();
    }
  }

  private void resetToStart() {
    mp.stop();
    mp.seek(Duration.millis(0));
  }

  private StringBinding getTimeRemainingProperty() {
    return Bindings.createStringBinding(() -> {
      Duration currentTime = mp.getCurrentTime();
      Duration totalDuration = mp.getTotalDuration();
      if (currentTime == null || totalDuration == null) {
        return "-00:00";
      }
      double currentSeconds = currentTime.toSeconds();
      double totalSeconds = totalDuration.toSeconds();
      int diff = (int) totalSeconds - (int) currentSeconds;
      int minutes = diff / 60;
      int rest = diff - (minutes * 60);
      return String.format("-%02d:%02d", minutes, rest);
    }, mp.currentTimeProperty(), mp.totalDurationProperty(), mp.statusProperty());
  }

  private DoubleBinding getMusicProgressValueProperty() {
    return Bindings.createDoubleBinding(() -> {
      if (mp.getCurrentTime() == null || mp.getTotalDuration() == null) {
        return 0.0;
      }
      return mp.getCurrentTime().toMillis() / mp.getTotalDuration().toMillis();
    }, mp.currentTimeProperty(), mp.totalDurationProperty());
  }

  private StringBinding getCurrentTimeProperty() {
    return Bindings.createStringBinding(() -> {
      double totalSeconds = mp.getCurrentTime().toSeconds();
      int minutes = (int) totalSeconds / 60;
      int rest = (int) totalSeconds - (minutes * 60);
      return String.format("%02d:%02d", minutes, rest);
    }, mp.currentTimeProperty(), mp.totalDurationProperty(), mp.statusProperty());
  }

  private AudioSpectrumListener createSpectrumListener() {
    return (timestamp, duration, magnitudes, phases) -> {
      this.spectrum = magnitudes;
      this.phases = phases;
    };
  }

  private void initColors() {
    final float frequency = 0.3f;
    List<Color> colors = new ArrayList<>();
    for (int i = 0; i < numberOfBandsProperty.get().intValue(); i++) {
      double red = Math.sin(frequency * i + 0) * 127 / 255f + 128 / 255f;
      double green = Math.sin(frequency * i + 2) * 127 / 255f + 128 / 255f;
      double blue = Math.sin(frequency * i + 4) * 127 / 255f + 128 / 255f;
      colors.add(new Color(red, green, blue, 1));
    }
    this.colors.setAll(colors);
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
    if (spectrum == null) {
      return;
    }

    final int numOfBands = numberOfBandsProperty.get().intValue();
    final float fullLineWidth = (float) CANVAS_WIDTH / numOfBands;
    final float lineWidth = fullLineWidth * 0.8f;
    final float lineOffset = (fullLineWidth - lineWidth) / 2;

    final float padding = 30;
    final float textHeight = 45;
    final float spectrumAreaHeight = (CANVAS_HEIGHT - 2 * padding - textHeight) * 0.85f;
    final float phaseAreaHeight = (CANVAS_HEIGHT - 2 * padding - textHeight) * 0.15f;
    final float scaleSpectrum = -spectrumAreaHeight / thresholdProperty.get().floatValue();

    final float threshholdValue = -thresholdProperty.get().floatValue();
    final boolean fileIsNull = selectedFileProperty.get() == null;
    final boolean notPlaying = !fileIsNull && !musicPlayingProperty.get();
    final boolean initialValues = fileIsNull || notPlaying;
    final float scalePhase = (float) (phaseAreaHeight / Math.PI);

    ctx.clearRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    try {
      for (int i = 0; i < numOfBands; i++) {
        ctx.setFill(colors.get(i));
        float value = initialValues ? threshholdValue : -spectrum[i];
        float lineHeight = value * scaleSpectrum;
        ctx.fillRoundRect(0 + i * fullLineWidth + lineOffset, padding + spectrumAreaHeight - lineHeight, lineWidth, lineHeight, 5, 5);
      }

      for (int i = 0; i < numOfBands; i++) {
        Color col = colors.get(i);
        Color fadedCol = col.deriveColor(col.getHue(), col.getSaturation(), col.getBrightness(), 0.5);
        ctx.setFill(fadedCol);
        float value = initialValues ? (float) Math.PI : phases[i];
        value = value * scalePhase;
        ctx.fillRoundRect((CANVAS_WIDTH - lineWidth) - (i * fullLineWidth + lineOffset), CANVAS_HEIGHT - padding - phaseAreaHeight, lineWidth, value,
            ARC, ARC);
      }
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public static void main(String[] args) {
    LogManager.getLogManager().reset();
    launch(args);
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    chooser = new FileChooser();
    chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Music Files", "*.mp3", "*.m4a", "*.wav"));
    openFileMenu.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
    final ImageView playImg = new ImageView(new Image(getClass().getResourceAsStream("/resources/play.png")));
    final ImageView pauseImg = new ImageView(new Image(getClass().getResourceAsStream("/resources/pause.png")));
    final ImageView musicImg = new ImageView(new Image(getClass().getResourceAsStream("/resources/music.png"), 28, 28, true, true));
    btnSelectSong.setGraphic(musicImg);
    ctx = canvas.getGraphicsContext2D();
    bgctx = bgCanvas.getGraphicsContext2D();
    colors = FXCollections.observableArrayList();
    initProperties(playImg, pauseImg);
    initColors();
    initBackgroundCanvas();
    initMusicData();
    initTimeline();
    spectrum = new float[numberOfBandsProperty.get().intValue()];
    phases = new float[numberOfBandsProperty.get().intValue()];
  }

  private void initBackgroundCanvas() {
    final float padding = 30;
    final float textHeight = 45;
    final float spectrumAreaHeight = (CANVAS_HEIGHT - 2 * padding - textHeight) * 0.85f;
    bgctx.setFill(Color.BLACK);
    bgctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    bgctx.setFill(Color.YELLOW);
    bgctx.setFont(Font.font("Play", 30));
    bgctx.setTextAlign(TextAlignment.RIGHT);
    bgctx.fillText("wave", CANVAS_WIDTH - 10, padding + spectrumAreaHeight + 30);
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
    playButton.setOnAction(e -> playPause());
    fileDirtyProperty.addListener(e -> reloadMusicData());
    cmbNumberOfBands.getItems().setAll(8, 12, 24, 32, 50, 64, 80, 128);
    cmbNumberOfBands.valueProperty().bindBidirectional(numberOfBandsProperty);
    Tooltip tooltip = new Tooltip("Threshold value in dB");
    cmbThreshold.setTooltip(tooltip);
    cmbThreshold.getItems().setAll(-60, -80, -90, -100, -110, -120);
    cmbThreshold.valueProperty().bindBidirectional(thresholdProperty);
    musicProgress.setOnMouseClicked(e -> seek(e));
  }

  private void playPause() {
    if (musicPlayingProperty.get()) {
      mp.pause();
    } else {
      mp.play();
    }
  }

  private void reloadMusicData() {
    initMusicData();
    if (artistName != null && title != null) {
      titleLabel.setText(String.join(" - ", artistName, title));
    }
  }

  private void seek(MouseEvent e) {
    if (selectedFileProperty.get() != null) {
      final int cursorWidth = 4;
      double dx = e.getX() + cursorWidth;
      double width = musicProgress.getWidth();
      double percent = dx / width;
      mp.seek(Duration.millis(percent * mp.getTotalDuration().toMillis()));
    }
  }
}
