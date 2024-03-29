package domain;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

public class QRCodeGenerator {

	public static final String FILE_NAME = "QRCodes";

	public String generateQRCode(String user, double amount) throws Exception {

		String s = String.valueOf(amount);
		String data = user + "_" + s;

		File dir = new File(FILE_NAME);

		if (!dir.exists()) {
			dir.mkdirs();
			System.out.println("Diretorio " + FILE_NAME + " criado");
		}

		String uniqueID = UUID.randomUUID().toString();
		String path = ".\\" + FILE_NAME + "\\" + uniqueID + ".jpg";

		BitMatrix matrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 500, 500);

		MatrixToImageWriter.writeToPath(matrix, "jpg", Paths.get(path));

		return uniqueID;
	}

	public String readQRCode(String QRCode) {
		try {
			String path = ".\\" + FILE_NAME + "\\" + QRCode + ".jpg";

			FileInputStream fi = new FileInputStream(path);
			BufferedImage bf = ImageIO.read(fi);

			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(bf)));

			Result result = new MultiFormatReader().decode(bitmap);
			String s = result.getText();
			fi.close();
			File myObj = new File(path);

			if (!myObj.exists()) {
				return "fileNotExists";
			}
			myObj.delete();

			return s;

		} catch (Exception e) {
			/* Do nothing */
		}
		return null;
	}

}
