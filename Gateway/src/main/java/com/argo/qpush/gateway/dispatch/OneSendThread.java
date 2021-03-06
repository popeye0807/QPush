package com.argo.qpush.gateway.dispatch;

import com.argo.qpush.core.MetricBuilder;
import com.argo.qpush.core.entity.*;
import com.argo.qpush.core.service.ClientService;
import com.argo.qpush.core.service.PayloadService;
import com.argo.qpush.gateway.Connection;
import com.argo.qpush.gateway.SentProgress;
import com.argo.qpush.gateway.keeper.APNSKeeper;
import com.argo.qpush.gateway.keeper.ConnectionKeeper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.Callable;

/**
 *
 * 1对1或1对多的推送
 *
 * Created by yaming_deng on 14-8-8.
 */
public class OneSendThread implements Callable<Integer> {

    protected static Logger logger = LoggerFactory.getLogger(OneSendThread.class);

    private Payload message;
    private Product product;
    private SentProgress progress;

    public OneSendThread(final Product product, final Payload message, final SentProgress progress) {
        super();
        this.message = message;
        this.product = product;
        this.progress = progress;
    }

    @Override
    public Integer call() throws Exception {
        if(message == null){
            return 0;
        }

        if(message.getClients()!=null){
            SentProgress thisProg = new SentProgress(message.getClients().size());
            for (String client : message.getClients()){
                Connection c = ConnectionKeeper.get(product.getAppKey(), client);
                if(c != null) {
                    c.send(thisProg, message);
                }else{
                    if (product.getClientTypeid().intValue() != ClientType.iOS){
                        continue;
                    }
                    Client cc = ClientService.instance.findByUserId(client);
                    if (cc == null){
                        logger.warn("Client not found. client=" + client);
                        continue;
                    }
                    if (!cc.isDevice(ClientType.iOS)){
                        continue;
                    }
                    if (StringUtils.isBlank(cc.getDeviceToken())){
                        logger.error("Client's deviceToken not found. client=" + client);
                        continue;
                    }
                    APNSKeeper.push(thisProg, this.product, cc, message);
                }
            }

            try {
                thisProg.getCountDownLatch().await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            logger.info("SingSend Summary. id=" + message.getId() + ", " + thisProg);

            int total = thisProg.getSuccess().get();

            if (total > 0) {
                MetricBuilder.pushMeter.mark(total);
                MetricBuilder.pushSingleMeter.mark(total);
            }

            try {
                if (message.getStatusId().intValue() == PayloadStatus.Pending0) {
                    message.setTotalUsers(total);
                    message.setSentDate(new Date().getTime()/1000);
                    message.setStatusId(PayloadStatus.Sent);
                    PayloadService.instance.saveWithId(message);
                }else {
                    PayloadService.instance.updateSendStatus(message, total);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            this.progress.incrSuccess();

            return total;
        }

        return 0;
    }

}
