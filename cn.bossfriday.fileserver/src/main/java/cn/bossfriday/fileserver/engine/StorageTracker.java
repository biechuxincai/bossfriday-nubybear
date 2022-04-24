package cn.bossfriday.fileserver.engine;

import cn.bossfriday.common.router.ClusterRouterFactory;
import cn.bossfriday.common.router.RoutableBean;
import cn.bossfriday.common.router.RoutableBeanFactory;
import cn.bossfriday.common.rpc.actor.ActorRef;
import cn.bossfriday.fileserver.common.enums.OperationResult;
import cn.bossfriday.fileserver.http.FileServerHttpResponseHelper;
import cn.bossfriday.fileserver.rpc.module.UploadResult;
import cn.bossfriday.fileserver.rpc.module.WriteTmpFileMsg;
import cn.bossfriday.fileserver.rpc.module.WriteTmpFileResult;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static cn.bossfriday.fileserver.common.FileServerConst.*;

@Slf4j
public class StorageTracker {
    private volatile static StorageTracker instance = null;

    @Getter
    private ActorRef trackerActor;

    private StorageTracker() {
        try {
            trackerActor = ClusterRouterFactory.getClusterRouter().getActorSystem().actorOf(ACTOR_FS_TRACKER);
        } catch (Exception ex) {
            log.error("StorageTracker error!", ex);
        }
    }

    /**
     * getInstance
     */
    public static StorageTracker getInstance() {
        if (instance == null) {
            synchronized (StorageTracker.class) {
                if (instance == null) {
                    instance = new StorageTracker();
                }
            }
        }

        return instance;
    }

    /**
     * onPartialUploadDataReceived
     */
    public void onPartialUploadDataReceived(WriteTmpFileMsg msg) throws Exception {
        // 按fileTransactionId路由
        RoutableBean routableBean = RoutableBeanFactory.buildKeyRouteBean(msg.getFileTransactionId(), ACTOR_FS_TMP_FILE, msg);
        ClusterRouterFactory.getClusterRouter().routeMessage(routableBean, trackerActor);
    }

    /**
     * onWriteTmpFileResultReceived
     */
    public void onWriteTmpFileResultReceived(WriteTmpFileResult msg) throws Exception {
        if (msg.getResult().getCode() != OperationResult.OK.getCode()) {
            FileServerHttpResponseHelper.sendResponse(msg.getFileTransactionId(), HttpResponseStatus.INTERNAL_SERVER_ERROR, msg.getResult().getMsg());

            return;
        }

        // 强制路由：同一个fileTransaction要求在同一个集群节点处理
        RoutableBean routableBean = RoutableBeanFactory.buildForceRouteBean(msg.getClusterNodeName(), ACTOR_FS_UPLOAD, msg);
        ClusterRouterFactory.getClusterRouter().routeMessage(routableBean, trackerActor);
    }

    /**
     * onUploadResultReceived
     */
    public void onUploadResultReceived(UploadResult msg) {

    }
}