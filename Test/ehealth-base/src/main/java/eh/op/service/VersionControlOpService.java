package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcService;
import eh.base.dao.VersionControlDAO;
import eh.entity.base.VersionControl;

/**
 * Created by houxr on 2016/8/24.
 */
public class VersionControlOpService {

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
    @RpcService
    public VersionControl updateVersionControlForOP(VersionControl versionControl) {
        VersionControlDAO versionControlDAO = DAOFactory.getDAO(VersionControlDAO.class);
        return versionControlDAO.updateVersionControlForOP(versionControl);
    }

    /**
     * 运营平台版本更新日志查询
     *
     * @param prgType    程序类型:1纳里医生APP 2纳里健康APP 3纳里医生PC版 4运营平台
     * @param clientType 终端类型:1PC  2IOS  3android 4微信
     * @return
     * @author houxr 2016-08-24
     */
    @RpcService
    public QueryResult<VersionControl> queryVersionControlResultByStartAndLimit(final Integer prgType, final Integer clientType,
                                                                                     final int start, final int limit) {
        VersionControlDAO versionControlDAO = DAOFactory.getDAO(VersionControlDAO.class);
        return versionControlDAO.queryVersionControlQueryResultByStartAndLimit(prgType, clientType, start, limit);

    }

}
