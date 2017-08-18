package eh.bus.service.image;

import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;


public class ImageCompressUtilTask implements Callable<String>{

	public static final Logger log = Logger.getLogger(ImageCompressUtilTask.class);

	private int fileId;
	
    public ImageCompressUtilTask(int fileId){
        this.fileId = fileId;
    }
    
    @Override
    public String call() throws Exception {
    	return imageCompress(fileId);  
    }

	/**
	 * 从ssdev中获取的图片资源，通过参数对图片按比例压缩，默认比例系数为0.5
	 * 
	 * @param fileId
	 * @return
	 */
	public static String imageCompress(int fileId) {		
		try {
			FileMetaRecord f = FileService.instance().load(fileId);
			InputStream is = FileService.instance().getRepository()
					.readAsStream(f);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			Thumbnails.of(is).scale(0.5f).toOutputStream(os);
			return Base64.encodeBase64String(IOUtils.toByteArray(parse(os)));
		} catch (Exception e) {
			log.error("imageCompress() error : "+e);
		}
		return "";
	}

	/**
	 * IO流转换 outputStream->inputStream
	 * 
	 * @param out
	 * @return
	 * @throws Exception
	 */
	public static ByteArrayInputStream parse(OutputStream out) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos = (ByteArrayOutputStream) out;
		ByteArrayInputStream swapStream = new ByteArrayInputStream(
				baos.toByteArray());
		return swapStream;
	}

}
