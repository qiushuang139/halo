package run.halo.app.handler.file;



import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.exception.FileOperationException;
import run.halo.app.model.enums.AttachmentType;
import run.halo.app.model.properties.TencentYunProperties;
import run.halo.app.model.support.UploadResult;
import run.halo.app.service.OptionService;
import run.halo.app.utils.FilenameUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * TencentYun file handler.
 *
 * @author wangya
 * @date 2019-07-25
 */
@Slf4j
@Component
public class TencentYunFileHandler implements FileHandler {

    private final OptionService optionService;

    public TencentYunFileHandler(OptionService optionService) {
        this.optionService = optionService;
    }

    @Override
    public UploadResult upload(MultipartFile file) {
        Assert.notNull(file, "Multipart file must not be null");

        // Get config
        String ossRegion = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_REGION).toString();
        String ossAccessKey = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_ACCESS_KEY).toString();
        String ossAccessSecret = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_ACCESS_SECRET).toString();
        String ossBucketName = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_BUCKET_NAME).toString();
        String ossStyleRule = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_STYLE_RULE).toString();
        String ossSource = StringUtils.join("https://", ossBucketName, ".cos." + ossRegion+".myqcloud.com");

        //get file attribute
        long size = file.getSize();
        String contentType = file.getContentType();



        COSCredentials cred = new BasicCOSCredentials(ossAccessKey, ossAccessSecret);
        Region region = new Region(ossRegion);
        ClientConfig clientConfig = new ClientConfig(region);


        // Init OSS client
        COSClient cosClient = new COSClient(cred, clientConfig);



        try {
            String basename = FilenameUtils.getBasename(file.getOriginalFilename());
            String extension = FilenameUtils.getExtension(file.getOriginalFilename());
            String timestamp = String.valueOf(System.currentTimeMillis());
            String upFilePath = StringUtils.join(basename, "_", timestamp, ".", extension);
            String filePath = StringUtils.join(StringUtils.appendIfMissing(ossSource, "/"), upFilePath);

            // Upload
            ObjectMetadata objectMetadata = new ObjectMetadata();
            //提前告知输入流的长度, 否则可能导致 oom
            objectMetadata.setContentLength(size);
            // 设置 Content type, 默认是 application/octet-stream
            objectMetadata.setContentType(contentType);
            PutObjectResult putObjectResponseFromInputStream = cosClient.putObject(ossBucketName, upFilePath, file.getInputStream(),objectMetadata);
            if (putObjectResponseFromInputStream == null) {
                throw new FileOperationException("上传附件 " + file.getOriginalFilename() + " 到腾讯云失败 ");
            }

            // Response result
            UploadResult uploadResult = new UploadResult();
            uploadResult.setFilename(basename);
            uploadResult.setFilePath(filePath);
            uploadResult.setKey(upFilePath);
            uploadResult.setMediaType(MediaType.valueOf(Objects.requireNonNull(file.getContentType())));
            uploadResult.setSuffix(extension);
            uploadResult.setSize(file.getSize());

            // Handle thumbnail
            if (FileHandler.isImageType(uploadResult.getMediaType())) {
                BufferedImage image = ImageIO.read(file.getInputStream());
                uploadResult.setWidth(image.getWidth());
                uploadResult.setHeight(image.getHeight());
                uploadResult.setThumbPath(StringUtils.isBlank(ossStyleRule) ? filePath : filePath + ossStyleRule);
            }

            return uploadResult;
        } catch (Exception e) {
            throw new FileOperationException("附件 " + file.getOriginalFilename() + " 上传失败(腾讯云)", e);
        } finally {
            cosClient.shutdown();
        }
    }

    @Override
    public void delete(String key) {
        Assert.notNull(key, "File key must not be blank");

        // Get config
        String ossRegion = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_REGION).toString();
        String ossAccessKey = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_ACCESS_KEY).toString();
        String ossAccessSecret = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_ACCESS_SECRET).toString();
        String ossBucketName = optionService.getByPropertyOfNonNull(TencentYunProperties.OSS_BUCKET_NAME).toString();
        String ossSource = StringUtils.join("https://", ossBucketName, ".cos." + ossRegion+".myqcloud.com");

        COSCredentials cred = new BasicCOSCredentials(ossAccessKey, ossAccessSecret);
        Region region = new Region(ossRegion);
        ClientConfig clientConfig = new ClientConfig(region);


        // Init OSS client
        COSClient cosClient = new COSClient(cred, clientConfig);

        try {
            cosClient.deleteObject(ossBucketName, key);
        } catch (Exception e) {
            throw new FileOperationException("附件 " + key + " 从腾讯云删除失败", e);
        } finally {
            cosClient.shutdown();
        }
    }

    @Override
    public boolean supportType(AttachmentType type) {
        return AttachmentType.TENCENTYUN.equals(type);
    }
}
