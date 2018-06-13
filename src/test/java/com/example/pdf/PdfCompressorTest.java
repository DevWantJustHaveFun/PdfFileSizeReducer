package com.example.pdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.pdf.PdfCompressor;

public class PdfCompressorTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PdfCompressorTest.class);

    @Test
    public void testReducingPdf() throws Exception
    {
        LOGGER.info("Start pdf compression");

        final File directory = new File("/tmp/test");
        final File[] fList = directory.listFiles();

        for (final File file : fList) {
            compress(file);
        }

    }

    private void compress(final File input) throws Exception
    {
        final long startTime = System.currentTimeMillis();

        final File output = new File(input.getName() + ".compressed.pdf");

        output.delete();

        try (FileInputStream is = new FileInputStream(input);
                FileOutputStream os = new FileOutputStream(output)) {

            PdfCompressor.reduce(is, os, 0.5f, 0.5f, 1024, 768);
            final long endTime = System.currentTimeMillis();

            LOGGER.info("Compress {} in {} ms - result {} - {} = {}", input.getName(), endTime - startTime, input.length(), output.length(), input.length() - output.length());
        }
    }

}