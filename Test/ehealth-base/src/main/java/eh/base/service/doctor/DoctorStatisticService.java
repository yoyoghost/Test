package eh.base.service.doctor;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import ctd.util.converter.ConversionUtils;
import eh.base.dao.DoctorStatisticDAO;
import eh.entity.base.DoctorStatistic;
import eh.utils.DateConversion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2017/7/19.
 */
public class DoctorStatisticService {

    public static final Logger logger = LoggerFactory.getLogger(DoctorStatisticService.class);

    /**
     * 计算患者和医生关注此医生的数量
     */
    @RpcService
    public void autoCalculateAttentionCount() {

        logger.info("base autoCalculateAttentionCount is running");
        DoctorStatisticDAO doctorStatisticDAO = DAOFactory.getDAO(DoctorStatisticDAO.class);
        List<Object[]> doctorAttentionCountList = doctorStatisticDAO.autoCalculateAttentionCount();
        DoctorStatistic doctorStatistic;
        for (Object[] objects : doctorAttentionCountList) {
            Integer doctorId = ConversionUtils.convert(objects[0], Integer.class);
            Long attentionCount = ConversionUtils.convert(objects[1], Long.class);
            DoctorStatistic returnDoctorStatistic = doctorStatisticDAO.get(doctorId);
            if (ObjectUtils.isEmpty(returnDoctorStatistic)) {
                doctorStatistic = initDoctorStatisticForAttentionCount(doctorId, attentionCount);
                doctorStatisticDAO.save(doctorStatistic);
            } else {
                doctorStatisticDAO.updateFeedbackCountToInitByTypeAndIdForSchedule(attentionCount, doctorId, DateConversion.getFormatDate(new Date(),
                        DateConversion.DEFAULT_DATE_TIME));
            }

        }
    }

    /**
     * 包装新增一条数据,但是数据只有关注数
     *
     * @param doctorId       医生的主键
     * @param attentionCount 患者和医生关注此医生的数量
     * @return
     * @author cuill
     * @date 2017/7/19
     */
    private DoctorStatistic initDoctorStatisticForAttentionCount(Integer doctorId, Long attentionCount) {
        DoctorStatistic doctorStatistic;
        doctorStatistic = new DoctorStatistic();
        doctorStatistic.setDoctorId(doctorId);
        doctorStatistic.setAttentionCount(attentionCount);
        doctorStatistic.setCreateTime(DateConversion.getFormatDate(new Date(),
                DateConversion.DEFAULT_DATE_TIME));
        doctorStatistic.setModifyTime(DateConversion.getFormatDate(new Date(),
                DateConversion.DEFAULT_DATE_TIME));
        return doctorStatistic;
    }
}
