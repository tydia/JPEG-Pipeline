# JPEG Pipeline
## Usage
1. Compile with `javac CompressImage.java`
2. Run with:  `java CompressImage InputImage.rgb quantizationLevel DeliveryMode Latency`
	 - InputImage is the **.rgb** format (not jpg format) image to input to the coder-decoder (1920x1080 images only).
	 - QuantizationLevel – a factor that will decrease/increase compression as explained below. This value will range from 0 to 7.
	 - DeliveryMode – an index ranging from 1, 2, 3. 1 is for baseline delivery (I.e. sequential), 2 implies progressive delivery using spectral selection, 3 implies progressive delivery using successive bit approximation.
	 - Latency – a variable in milliseconds, which will give a suggestive “sleep” time between data blocks during decoding. This parameter will be used to “simulate” decoding as data arrives across low and high band width communication networks.
	 - Example Run: `CompressImage Example.rgb 3 2 100`  Here you are encoding Example.rgb and using a 3 quantization level. You are using the spectral selection mode and there is a latency while decoding and so you should see the output data blocks appear as they get decoded.

## Sidenote
1. For mode 2 and 3, the original image is showed for 2 seconds for JFrame to startup. So the actual results are showed after 2 seconds of the first frame. This delay only happens before main loop.

3. For mode 3, since each iteration processes more information than mode 2, so you will need to wait 1 to 2 seconds in addition to 2 seconds delay for Jframe to start. This means you will see the original image after 3 to 4 seconds.

3. For mode 2 and 3, iteration number is printed out in terminal.
