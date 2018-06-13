package com.example.pdf;

import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.sanselan.ImageReadException;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfStream;
import com.itextpdf.text.pdf.parser.PdfImageObject;

public final class PdfCompressor
{
    private static final float DEFAULT_SCALE_FACTOR = 1.0f;
    private static final String ICC_PROFILE_FILE_NAME = "ISOcoated_v2_300_eci.icc";

    private PdfCompressor()
    {

    }

    public static void reduce(final InputStream inputStream, final OutputStream outputStream, final float scaleFactor,
            final float jpegCompressionFactor, final int resizeExceptWidthUnder, final int resizeExceptHeightUnder) throws IOException, DocumentException
    {
        final PdfReader pdfReader = new PdfReader(inputStream);
        final int size = pdfReader.getXrefSize();
        final ICC_ColorSpace cmykCS = getCmykCS(null);

        // Look for image and manipulate image stream
        for (int i = 0; i < size; i++) {
            final PdfObject object = pdfReader.getPdfObject(i);

            if (object == null || !object.isStream()) {
                continue;
            }

            final PRStream stream = (PRStream) object;

            // if it is not a jpeg filter
            if (PdfName.IMAGE.equals(stream.getAsName(PdfName.SUBTYPE)) && PdfName.DCTDECODE.equals(stream.getAsName(PdfName.FILTER))) {
                doJpegCompression(stream, scaleFactor, jpegCompressionFactor, cmykCS, resizeExceptWidthUnder, resizeExceptHeightUnder);
            }
        }

        // Save altered PDF
        final PdfStamper stamper = new PdfStamper(pdfReader, outputStream);
        stamper.setFullCompression();
        stamper.close();
        pdfReader.close();
    }

    private static void doJpegCompression(final PRStream stream, float scaleFactor, final float jpegCompressionFactor, final ICC_ColorSpace cmykCS,
            final int resizeExceptWidthUnder,
            final int resizeExceptHeightUnder) throws IOException
    {
        final PdfImageObject image = new PdfImageObject(stream);

        final int imageWidth = image.getBufferedImage().getRaster().getWidth();
        final int imageHeight = image.getBufferedImage().getRaster().getHeight();

        if (!(resizeExceptWidthUnder < imageWidth && resizeExceptHeightUnder < imageHeight)) {
            scaleFactor = DEFAULT_SCALE_FACTOR;
        }

        final int width = (int) (imageWidth * scaleFactor);
        final int height = (int) (imageHeight * scaleFactor);

        try {

            final ByteArrayOutputStream output = JpegCompressor.compress(image.getImageAsBytes(), cmykCS, scaleFactor, jpegCompressionFactor);

            stream.clear();
            stream.setData(output.toByteArray(), false, PdfStream.NO_COMPRESSION);
            stream.put(PdfName.TYPE, PdfName.XOBJECT);
            stream.put(PdfName.SUBTYPE, PdfName.IMAGE);
            stream.put(PdfName.FILTER, PdfName.DCTDECODE);
            stream.put(PdfName.WIDTH, new PdfNumber(width));
            stream.put(PdfName.HEIGHT, new PdfNumber(height));
            stream.put(PdfName.BITSPERCOMPONENT, new PdfNumber(8));
            stream.put(PdfName.COLORSPACE, PdfName.DEVICERGB);

        }
        catch (final IOException e) {
            throw e;
        }
        catch (final ImageReadException e) {
            throw new IOException(e);
        }
    }

    private static ICC_ColorSpace getCmykCS(final ICC_Profile cmykProfile) throws IOException
    {
        if (cmykProfile == null) {
            return new ICC_ColorSpace(ICC_Profile.getInstance(ClassLoader.getSystemResourceAsStream(ICC_PROFILE_FILE_NAME)));
        }

        return new ICC_ColorSpace(cmykProfile);
    }
}
