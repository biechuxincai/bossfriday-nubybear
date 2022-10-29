package cn.bossfriday.fileserver.engine.impl.v1;

import cn.bossfriday.common.exception.BizException;
import cn.bossfriday.common.utils.Base58Util;
import cn.bossfriday.fileserver.engine.core.CurrentStorageEngineVersion;
import cn.bossfriday.fileserver.engine.core.IMetaDataHandler;
import cn.bossfriday.fileserver.engine.entity.MetaDataIndex;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static cn.bossfriday.fileserver.common.FileServerConst.DEFAULT_STORAGE_ENGINE_VERSION;
import static cn.bossfriday.fileserver.common.FileServerConst.URL_DOWNLOAD;
import static cn.bossfriday.fileserver.engine.entity.MetaDataIndex.HASH_CODE_LENGTH;

/**
 * MetaDataHandler
 *
 * @author chenx
 */
@Slf4j
@CurrentStorageEngineVersion
public class MetaDataHandler implements IMetaDataHandler {

    @Override
    public Long getMetaDataTotalLength(String fileName, long fileTotalSize) {
        return this.getMetaDataLength(fileName) + fileTotalSize;
    }

    @Override
    public int getMetaDataLength(String fileName) {
        /**
         * storeEngineVersion 1字节
         * fileStatus 1字节
         * timestamp 8字节
         * fileName utf8字符串（前2字节为字符串长度）
         * fileTotalSize 8字节
         */
        return 1 + 1 + 8 + 2 + fileName.getBytes(StandardCharsets.UTF_8).length + 8;
    }

    @Override
    public String downloadUrlEncode(MetaDataIndex metaDataIndex) throws IOException {
        byte[] bytes = metaDataIndex.serialize();
        obfuscateMetaDataIndex(bytes);
        String encodedMetaDataString = Base58Util.encode(bytes);

        return "/" + URL_DOWNLOAD + "/v" + DEFAULT_STORAGE_ENGINE_VERSION + "/" + encodedMetaDataString + "." + metaDataIndex.getFileExtName();
    }

    @Override
    public MetaDataIndex downloadUrlDecode(String input) throws IOException {
        byte[] bytes = Base58Util.decode(input);
        obfuscateMetaDataIndex(bytes);
        MetaDataIndex metaDataIndex = new MetaDataIndex().deserialize(bytes);
        if (metaDataIndex.getStoreEngineVersion() != DEFAULT_STORAGE_ENGINE_VERSION) {
            throw new BizException("invalid storageEngineVersion!");
        }

        return metaDataIndex;
    }

    /**
     * obfuscateMetaDataIndex 混淆MetaDataIndex
     *
     * @param bytes
     */
    private static void obfuscateMetaDataIndex(byte[] bytes) {
        if (bytes == null) {
            throw new BizException("bytes is null");
        }

        if (bytes.length <= HASH_CODE_LENGTH) {
            throw new BizException("bytes.length <= " + HASH_CODE_LENGTH);
        }

        int leftBytesSize = bytes.length - HASH_CODE_LENGTH;
        byte[] hashBytes = new byte[HASH_CODE_LENGTH];
        byte[] leftBytes = new byte[leftBytesSize];

        System.arraycopy(bytes, 0, hashBytes, 0, HASH_CODE_LENGTH);
        System.arraycopy(bytes, HASH_CODE_LENGTH, leftBytes, 0, leftBytesSize);
        for (int i = HASH_CODE_LENGTH; i < bytes.length; i++) {
            bytes[i] = (byte) (hashBytes[i % HASH_CODE_LENGTH] ^ leftBytes[i - HASH_CODE_LENGTH]);
        }
    }
}
