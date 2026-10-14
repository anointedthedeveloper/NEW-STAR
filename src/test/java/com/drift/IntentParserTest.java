package com.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.drift.intents.Intent;
import com.drift.intents.IntentParser;
import com.drift.intents.Normalizer;
import org.junit.jupiter.api.Test;

class IntentParserTest {
    private final IntentParser parser = new IntentParser();

    @Test void normalizerStripsFillerAndPunctuation() {
        assertThat(Normalizer.normalize("Hey Drift, could you please Open Visual Studio Code?")).isEqualTo("open visual studio code");
        assertThat(Normalizer.normalize("  Open   Notepad. ")).isEqualTo("open notepad");
    }

    @Test void openApp() {
        assertThat(parser.parse("Open Visual Studio Code.")).isEqualTo(new Intent.OpenApp("visual studio code"));
        assertThat(parser.parse("open my coding editor")).isEqualTo(new Intent.OpenApp("coding editor"));
        assertThat(parser.parse("run notepad")).isEqualTo(new Intent.OpenApp("notepad"));
    }

    @Test void foldersFilesProjects() {
        assertThat(parser.parse("Open my downloads folder.")).isEqualTo(new Intent.OpenFolder("downloads"));
        assertThat(parser.parse("find my java projects")).isInstanceOf(Intent.FindProjects.class);
        assertThat(parser.parse("find budget report")).isEqualTo(new Intent.FindFiles("budget report"));
        assertThat(parser.parse("Open the project I worked on yesterday")).isEqualTo(new Intent.OpenRecentProject("yesterday"));
        assertThat(parser.parse("open project apollo")).isEqualTo(new Intent.OpenProject("apollo"));
    }

    @Test void developerTasksAndRawCommands() {
        assertThat(parser.parse("Run the approved Maven tests")).isEqualTo(new Intent.RunDevTask("maven-test"));
        assertThat(parser.parse("run maven clean")).isEqualTo(new Intent.RunDevTask("maven-clean"));
        assertThat(parser.parse("run command del everything")).isEqualTo(new Intent.RunRawCommand("del everything"));
        assertThat(parser.parse("run rm -rf slash")).isInstanceOf(Intent.RunRawCommand.class);
        assertThat(parser.parse("start the application")).isEqualTo(new Intent.RunDevTask("run-app"));
    }

    @Test void mediaAndCustomisation() {
        assertThat(parser.parse("What song is playing?")).isInstanceOf(Intent.MediaQuery.class);
        assertThat(parser.parse("Pause the music.")).isEqualTo(new Intent.MediaControl("pause"));
        assertThat(parser.parse("next song")).isEqualTo(new Intent.MediaControl("next"));
        assertThat(parser.parse("Switch to my coding theme")).isEqualTo(new Intent.SetTheme("coding"));
        assertThat(parser.parse("Make the widgets more transparent")).isEqualTo(new Intent.AdjustTransparency("more_transparent"));
        assertThat(parser.parse("make the widgets less transparent")).isEqualTo(new Intent.AdjustTransparency("less_transparent"));
        assertThat(parser.parse("Show the clock")).isEqualTo(new Intent.ShowWidget("clock"));
    }

    @Test void dictationAndLearning() {
        assertThat(parser.parse("start dictation")).isInstanceOf(Intent.StartDictation.class);
        assertThat(parser.parse("type hello world")).isEqualTo(new Intent.TypeText("hello world"));
        assertThat(parser.parse("Remember that I call Visual Studio Code my coding editor."))
                .isEqualTo(new Intent.Remember("coding editor", "visual studio code"));
    }

    @Test void unknownIsNotGuessed() {
        assertThat(parser.parse("the weather in space is blue")).isInstanceOf(Intent.Unknown.class);
        assertThat(parser.parse("")).isInstanceOf(Intent.Unknown.class);
    }
}
