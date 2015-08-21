package com.votors.umls;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Part-of-speech tagger using MaxEnt model
 *
 * @author Yifan Peng
 * @version 09/18/2013
 */
public class PosTagger extends BasicCmdLineTool {

    public static void main(String args[]) {
        PosTagger tagger = new PosTagger();
        tagger.run(args);
    }

    @Override
    public String getShortDescription() {
        return "Part-of-speech tagger using MaxEnt model";
    }

    @Override
    public String getHelp() {
        return "Usage: " + CLI.CMD + " " + getName()
                + " [MODEL] [INPUT] [OUTPUT]";
    }

    /**
     * Part-of-speech tagger
     *
     * @param args 0 - model name, absolute path;
     *             1 - input filename, absolute path;
     *             2 - output filename, absolute path
     */
    @Override
    public void run(String[] args) {

//        if (args.length != 3) {
//            System.out.println(getHelp());
//            return;
//        }

//        String modelPath = args[0];
//        String inputPath = args[1];
//        String outputPath = args[2];
        String modelPath = "C:\\fsu\\ra\\UmlsTagger\\data\\en-pos-maxent.bin";
        String inputPath = "C:\\fsu\\ra\\UmlsTagger\\data\\umls_output\\clinical_text.txt";
        String outputPath = "C:\\fsu\\ra\\UmlsTagger\\data\\en-pos-output.log";
        // read model
        InputStream modelIn = null;
        POSModel model = null;
        try {
            modelIn = new FileInputStream(modelPath);
            model = new POSModel(modelIn);
        } catch (IOException e) {
            // Model loading failed, handle the error
            e.printStackTrace();
            System.err.println("cannot read model: " + modelPath);
            System.err.println(getHelp());
            System.exit(1);
        }

        // initialize POSTaggerME
        POSTaggerME tagger = new POSTaggerME(model);

        // read input file
        ObjectStream<String> lineStream = null;
        PrintStream printer = null;
        String line = null;
        try {
            lineStream = new PlainTextByLineStream(new InputStreamReader(
                    new FileInputStream(inputPath)));
            printer = new PrintStream(new FileOutputStream(outputPath));

            PerformanceMonitor perfMon = new PerformanceMonitor(
                    System.err, "sent");
            perfMon.start();

            while ((line = lineStream.read()) != null) {
                if (line.isEmpty()) {
                    printer.println();
                } else if (line.startsWith("//")) {
                    printer.println(line);
                } else {
                    String[] sent = WhitespaceTokenizer.INSTANCE.tokenize(line);
                    String[] tags = tagger.tag(sent);
                    POSSample sample = new POSSample(sent, tags);
                    printer.println(sample.toString());
                }
                perfMon.incrementCounter();
            }
            lineStream.close();
            printer.close();
        } catch (IOException e) {
            // Model loading failed, handle the error
            e.printStackTrace();
            System.err.println(getHelp());
            System.exit(1);
        } finally {
            if (lineStream != null) {
                try {
                    lineStream.close();
                } catch (IOException e) {
                }
            }
            if (printer != null) {
                printer.close();
            }
        }
    }
}