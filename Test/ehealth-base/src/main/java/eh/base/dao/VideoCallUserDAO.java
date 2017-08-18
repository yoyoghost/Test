package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import eh.entity.base.VideoCallUser;

/**
 * 已废弃
 */
@Deprecated
public abstract class VideoCallUserDAO extends HibernateSupportDelegateDAO<VideoCallUser>
        implements DBDictionaryItemLoader<VideoCallUser> {

    public VideoCallUserDAO() {
        super();
        this.setEntityName(VideoCallUser.class.getName());
        this.setKeyField("id");
    }

    /**
     * 获取诚云账号
     *
     * @param relationId 关联账号-纳里医生用医生内码
     * @param role       角色-patient患者，doctor医生
     * @param domainName 域名
     * @return
     */
    @RpcService
    @DAOMethod(sql = "From VideoCallUser where relationId=:relationId and role=:role and domainName=:domainName and namePre=:namePre and mode=:mode")
    public abstract VideoCallUser getByFive(@DAOParam("relationId") int relationId,
                                            @DAOParam("role") String role, @DAOParam("domainName") String domainName,
                                            @DAOParam("namePre") String namePre, @DAOParam("mode") String mode);

    /**
     * 获取诚云账号名字
     *
     * @param relationId 关联账号-纳里医生用医生内码
     * @param role       角色-patient患者，doctor医生
     * @param domainName 域名
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select name From VideoCallUser where relationId=:relationId and role=:role and" +
            " domainName=:domainName and namePre=:namePre and mode=:mode")
    public abstract String getNameByFive(@DAOParam("relationId") int relationId,
                                         @DAOParam("role") String role, @DAOParam("domainName") String domainName,
                                         @DAOParam("namePre") String namePre, @DAOParam("mode") String mode);

    @RpcService
    @DAOMethod(sql = "select relationId from VideoCallUser where address=:address")
    public abstract Integer getRelationIdByAddress(@DAOParam("address") String address);
}
