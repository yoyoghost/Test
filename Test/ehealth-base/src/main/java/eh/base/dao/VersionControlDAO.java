package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.entity.base.VersionControl;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


public abstract class VersionControlDAO extends HibernateSupportDelegateDAO<VersionControl> {

    public VersionControlDAO() {
        super();
        this.setEntityName(VersionControl.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    @DAOMethod
    public abstract VersionControl getById(int id);

    /**
     * 查询程序最新版本的版本号
     *
     * @param prgType
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "from VersionControl where prgType=:prgType and versionType=1")
    public abstract VersionControl getByPrgType(@DAOParam("prgType") int prgType);

    /**
     * 检测版本更新服务
     *
     * @param prgType
     * @param version
     * @return
     * @author hyj
     */
    @RpcService
    public boolean checkVersion(Integer prgType, String version) {
        VersionControl v = this.getByPrgType(prgType);
        if (v == null) {
            throw new DAOException(609, "系统版本有误，请联系纳里客服");
        }
        if (v.getVersion().equals(version)) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 版本更新设置为历史版本
     *
     * @param prgType    程序类型
     * @param clientType 终端类型
     * @return
     */
    @DAOMethod(sql = "update VersionControl set versionType=0 where id=:id and prgType=:prgType and clientType=:clientType")
    public abstract void updateVersionTypeById(@DAOParam("id") Integer id, @DAOParam("prgType") Integer prgType,
                                               @DAOParam("clientType") Integer clientType);


    /**
     * 查询当前使用版本
     *
     * @param versionType
     * @param prgType     程序类型:1纳里医生APP 2纳里健康APP 3纳里医生PC版 4运营平台
     * @param clientType  终端类型:1PC  2IOS  3android 4微信
     * @return
     */
    @DAOMethod(sql = "from VersionControl where versionType=:versionType and prgType=:prgType and clientType=:clientType")
    public abstract VersionControl getVersioinControlByPrgTypeAndClientType(@DAOParam("versionType") Integer versionType,
                                                                            @DAOParam("prgType") Integer prgType,
                                                                            @DAOParam("clientType") Integer clientType);


    public void validateVersionControl(Integer prgType, Integer clientType, Integer updateStrategy) {
        if (ObjectUtils.isEmpty(prgType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "更新程序类型不能为空");
        }
        if (ObjectUtils.isEmpty(clientType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "终端类型不能为空");
        }
        if (ObjectUtils.isEmpty(updateStrategy)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "更新策略不能为空");
        }
    }

    /**
     * 运营平台更新版本
     *
     * @param versionControl 版本信息
     * @return
     * @Date 2016-08-23 houxr
     * @Desc PrgType程序类型:1纳里医生APP 2纳里健康APP 3纳里医生PC版 4运营平台
     * clientType终端类型:1PC  2IOS  3android 4微信
     * updateStrategy更新策略,对应策略表ID值 1:提醒一次 2:强制更新 3:定时 4:每次都提醒，默认1
     */
    public VersionControl updateVersionControlForOP(VersionControl versionControl) {
        if (ObjectUtils.isEmpty(versionControl)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "更新版本数据不能为空");
        }
        validateVersionControl(versionControl.getPrgType(), versionControl.getClientType(), versionControl.getUpdateStrategy());
        VersionControl oldVersionControl = this.getVersioinControlByPrgTypeAndClientType(1, versionControl.getPrgType(),
                versionControl.getClientType());
        if (!ObjectUtils.isEmpty(oldVersionControl)) {
            //设置上个版本为过期
            updateVersionTypeById(oldVersionControl.getId(), oldVersionControl.getPrgType(), oldVersionControl.getClientType());
        }
        VersionControl target = new VersionControl();
        target.setPrgType(versionControl.getPrgType());
        target.setPrgName(versionControl.getPrgName());
        target.setVersion(versionControl.getVersion());
        target.setVersionType(1);
        target.setUpdateContent(versionControl.getUpdateContent());
        target.setUpdateDate(new Timestamp(new Date().getTime()));
        target.setClientType(versionControl.getClientType());
        target.setChannelType(versionControl.getChannelType());
        target.setPrgAddress(versionControl.getPrgAddress());
        target.setUpdateStrategy(versionControl.getUpdateStrategy());
        target.setMd5(versionControl.getMd5());
        target = this.save(target);
        BusActionLogService.recordBusinessLog("版本更新", target.getId().toString(), "VersionControl", versionControl.getPrgName() + "更新" + versionControl.getVersion());
        return target;
    }

    /**
     * 运营平台版本更新日志查询
     *
     * @param prgType    程序类型:1纳里医生APP 2纳里健康APP 3纳里医生PC版 4运营平台
     * @param clientType 终端类型:1PC  2IOS  3android 4微信
     * @return
     * @author houxr 2016-08-24
     */
    public QueryResult<VersionControl> queryVersionControlQueryResultByStartAndLimit(final Integer prgType, final Integer clientType,
                                                                                     final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<VersionControl>> action = new AbstractHibernateStatelessResultAction<QueryResult<VersionControl>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = new StringBuilder("from VersionControl v where 1 = 1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(prgType)) {
                    hql.append(" and v.prgType=:prgType");
                    params.put("prgType", prgType);
                }
                if (!ObjectUtils.isEmpty(clientType)) {
                    hql.append(" and v.clientType=:clientType");
                    params.put("clientType", clientType);
                }
                hql.append(" order by v.updateDate desc ");

                Query query = ss.createQuery("select count(v.id) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery("select v " + hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<VersionControl> controlLists = query.list();
                QueryResult<VersionControl> result = new QueryResult<VersionControl>(total, query.getFirstResult(),
                        query.getMaxResults(), controlLists);
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<VersionControl>) action.getResult();
    }

}
