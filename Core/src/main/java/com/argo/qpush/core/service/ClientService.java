package com.argo.qpush.core.service;

import com.argo.qpush.core.entity.Client;
import com.argo.qpush.core.entity.ClientStatus;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

/**
 * 客户端管理类.
 * Created by yaming_deng on 14-8-11.
 */
public class ClientService extends BaseService {

    public static ClientService instance;

    protected static final RowMapper<Client> Client_ROWMAPPER = new BeanPropertyRowMapper<Client>(
            Client.class);

    @Override
    public void afterPropertiesSet() throws Exception {
        instance = this;
    }

    public void add(final Client client){
        if (client == null){
            return;
        }

        final String sql = "insert into client(productId, userId, deviceToken, createAt, statusId, typeId)values(?, ?, ?, ?, ?, ?)";
        KeyHolder holder = new GeneratedKeyHolder();
        this.mainJdbc.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(
                    Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setObject(1, client.getProductId());
                ps.setObject(2, client.getUserId());
                ps.setObject(3, client.getDeviceToken());
                ps.setObject(4, new Date());
                ps.setObject(5, ClientStatus.NewlyAdd);
                ps.setObject(6, client.getTypeId());
                return ps;
            }
        }, holder);

        client.setId(holder.getKey().longValue());
    }

    public Client findByUserId(String userId){
        String sql = "select * from client where userId = ?";
        List<Client> list = this.mainJdbc.query(sql, Client_ROWMAPPER, userId);
        if (list.size() > 0){
            return list.get(0);
        }
        return null;
    }

    public List<Client> findOfflineByType(Integer productId, Integer typeId, Integer page, Integer limit){
        long now = new Date().getTime()/1000 - 86400;
        int offset = (page - 1) * limit;
        String sql = "select * from client where productId = ? and typeId = ? and lastOnline >= ? and deviceToken is not null order by id limit ?, ?";
        List<Client> list = this.mainJdbc.query(sql, Client_ROWMAPPER, productId, typeId, now, offset, limit);
        return list;
    }

    public int countOfflineByType(Integer productId, Integer typeId){
        long now = new Date().getTime()/1000 - 86400;
        String sql = "select count(1) from client where productId = ? and typeId = ? and lastOnline >= ?";
        int count = this.mainJdbc.queryForObject(sql, Integer.class, productId, typeId, now);
        return count;
    }

    public void updateOnlineTs(long id){
        String sql = "update client set lastOnline=? where id = ?";
        this.mainJdbc.update(sql, new Date().getTime()/1000, id);
    }
}
