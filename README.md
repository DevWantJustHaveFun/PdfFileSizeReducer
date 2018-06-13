# PdfFileSizeReducer

An example for reduce size of pdf file based on jpeg image compression and scaling.
Based on the library IText 5.5

# How to use it 

1- build the jar 

`gradle clean build`

2- add the jar to your dependencies

3- use :

`PdfCompressor.reduce(inputStream, outputstream, scaleFactor, jpegCompressionFactor, resizeExceptWidthUnder, resizeExceptHeightUnder);`

# Reference
* https://developers.itextpdf.com/question/which-image-types-are-supported-itext
* http://huskdoll.tistory.com/347 for cmyk support
