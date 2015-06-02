package com.github.aldurd392.UnitedTweetsAnalyzer;

import java.io.IOException;

public class Main {
    public static void main( String[] args ) throws IOException {
        // FIXME: CLI parsing
        Geography geography = new Geography("/Users/aldur/Downloads/tl_2014_us_state/tl_2014_us_state.shp");

        Streamer streamer = new Streamer(geography);
        streamer.startListening();
    }
}
