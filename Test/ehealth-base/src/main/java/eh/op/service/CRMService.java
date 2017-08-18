package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcService;
import eh.entity.bus.CRMUser;
import eh.op.dao.CrmUserDAO;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by houxr on 16-6-13.
 * 运营平台CRM客户管理相关服务
 */
public class CRMService {

    /**
     * CRM统计信息
     *
     * @param patientName 客户姓名
     * @param idNumber    身份证号
     * @param patientSex  性别
     * @param minAge      年龄段0 19,...,70(70岁以上)
     * @param maxAge      年龄段18 25,...,150(70岁以上)
     * @param source      来源
     * @param wxSub       关注微信号
     * @param doctorId    推荐医生
     * @param requestMode 业务类型:1电话咨询2图文咨询3视频咨询
     * @param timeType    查询类型：1注册时间 2上次访问时间
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @return
     */
    @RpcService
    public Map<String, Object> queryCRMInfo(final String patientName, final String idNumber,
                                            final String patientSex, final Integer minAge, final Integer maxAge,
                                            final String source, final String wxSub,
                                            final Integer doctorId, final String requestMode,
                                            final Integer timeType, final Date startTime,
                                            final Date endTime) {
        Map<String, Object> resultMap = new HashedMap();
        CrmUserDAO crmUserDAO = DAOFactory.getDAO(CrmUserDAO.class);
        List<Object[]> patientSexList = crmUserDAO.countCRMUserBySex(patientName, idNumber, patientSex, minAge, maxAge,
                wxSub, requestMode, timeType, startTime, endTime);
        Long male = 0L;
        Long female = 0L;
        Long total = 0L;
        if (patientSexList.size() != 0) { // 返回值:[["1",12],["2",13]]
            for (Object[] objects : patientSexList) {
                if (StringUtils.equals((String) objects[0], "1")) {
                    male = (Long) objects[1];
                }
                if (StringUtils.equals((String) objects[0], "2")) {
                    female = (Long) objects[1];
                }
                total += (Long) objects[1];
            }
        }

        List<Object[]> countCount = crmUserDAO.countConsultCRMUser(patientName, idNumber, patientSex, minAge, maxAge,
                wxSub, requestMode, timeType, startTime, endTime);
        Long telConsultCount = 0L;
        Double telConsultAmount = 0.0d;
        Long telConsultCustomers = 0L;
        Long msgConsultCount = 0L;
        Double msgConsultAmount = 0.0d;
        Long msgConsultCustomers = 0L;
        //[[1,62,335,5798.1],[2,20,158,13.0]]
        //返回值:[[1,62(参与量),335(电话咨询量),5798.1(电话咨询总价)],[2,20(参与量),158(图文咨询量),13.0(图文咨询总价)]]
        if (countCount.size() != 0) {
            for (Object[] objects : countCount) {
                if ((Integer) objects[0] == 1) {
                    telConsultCustomers = (Long) objects[1];
                    telConsultCount = (Long) objects[2];
                    telConsultAmount = (Double) objects[3];
                }
                if ((Integer) objects[0] == 2) {
                    msgConsultCustomers = (Long) objects[1];
                    msgConsultCount = (Long) objects[2];
                    msgConsultAmount = (Double) objects[3];
                }
            }
        }
        Long payed = crmUserDAO.countPayedCRMUser(patientName, idNumber, patientSex,
                minAge, maxAge, wxSub, requestMode, timeType, startTime, endTime);
        resultMap.put("total", total);
        resultMap.put("female", female);
        resultMap.put("male", male);
        resultMap.put("payed", payed);
        resultMap.put("bizCount", telConsultCount + msgConsultCount);
        resultMap.put("bizAmount", telConsultAmount + msgConsultAmount);
        resultMap.put("consultCount", telConsultCount + msgConsultCount);
        resultMap.put("consultAmount", telConsultAmount + msgConsultAmount);
        resultMap.put("telConsultCount", telConsultCount);
        resultMap.put("telConsultCustomers", telConsultCustomers);
        resultMap.put("telConsultAmount", telConsultAmount);
        resultMap.put("msgConsultCount", msgConsultCount);
        resultMap.put("msgConsultCustomers", msgConsultCustomers);
        resultMap.put("msgConsultAmount", msgConsultAmount);
        return resultMap;
    }

    /**
     * 平台用户对象统计信息
     *
     * @param patientName 客户姓名
     * @param idNumber    身份证号
     * @param patientSex  性别
     * @param minAge      年龄段0~18 19~25,...,70~150(70岁以上)
     * @param source      来源
     * @param wxSub       关注微信号
     * @param doctorId    推荐医生
     * @param requestMode 业务类型:1电话咨询2图文咨询3视频咨询
     * @param timeType    查询类型：1注册时间 2上次访问时间
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param start       分页起始
     * @param limit       每页显示条数
     * @return
     */
    @RpcService
    public QueryResult<CRMUser> queryCRMUser(final String patientName, final String idNumber,
                                             final String patientSex, final Integer minAge, final Integer maxAge,
                                             final String source, final String wxSub,
                                             final Integer doctorId, final String requestMode,
                                             final Integer timeType, final Date startTime, final Date endTime,
                                             final Integer start, final Integer limit) {
        CrmUserDAO crmUserDAO = DAOFactory.getDAO(CrmUserDAO.class);
        QueryResult<CRMUser> crmUserList = crmUserDAO.queryListCRMUser(patientName, idNumber, patientSex, minAge, maxAge,
                wxSub, requestMode, timeType, startTime, endTime, start, limit);
        return crmUserList;
    }

}
