package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.QueryDoctorListDAO;
import eh.bus.dao.AppointDepartClassDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointSource;
import eh.entity.bus.DoctorDateSource;
import eh.utils.DateConversion;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class AlipayService {

	public static final Logger log = Logger.getLogger(AlipayService.class);

	/**
	 * 获取邵逸夫挂号科室列表
	 * 
	 * @param
	 * @return
	 */
	@RpcService
	public List<HashMap<String, Object>> findByOrganIDAndDepartClass(
			final Integer organID, final String branchId,
			final Integer departClass) {
		AppointDepartClassDAO dao = DAOFactory
				.getDAO(AppointDepartClassDAO.class);
		return dao.findByOrganIDAndDepartClass(organID, branchId, departClass);
	}

	/**
	 * 查询医生日期剩余号源总数服务
	 * 
	 * @param
	 * @return
	 */
	@RpcService
	public List<HashMap<String, Object>> totalByDoctorDateAndWorkDate(
			final int doctorId, final int sourceType, final String workDate,
			final int organId, final String appointDepartCode, final int isWeek) {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		String work = workDate;
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		try {
			// 工作日期
			date = df.parse(work);
		} catch (Exception e) {
			log.error("totalByDoctorDateAndWorkDate() error : "+e);
		}
		return dao.totalByDoctorDateAndWorkDate(doctorId, sourceType, date,
				organId, appointDepartCode, isWeek);
	}

	/**
	 * 查询医生一周排班服务
	 *
	 * @param
	 * @return
	 */
	@RpcService
	public List<HashMap<String, Object>> queryDoctorScheduleByWorkDate(
			final int doctorId, final String workDate, final int organId, final String appointDepartCode) {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		String work = workDate;
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		try {
			// 工作日期
			date = df.parse(work);
		} catch (Exception e) {
			log.error("queryDoctorScheduleByWorkDate() error : "+e);
		}
		return dao.queryDoctorScheduleByWorkDate(doctorId, date,
				organId, appointDepartCode);
	}
	/**
	 * 服务名:查询医生分时段号源服务
	 * 
	 * @author xyf
	 * @param doctorId
	 * @param sourceType
	 * @param workDate
	 * @param workType
	 * @param organID
	 * @param appointDepartCode
	 * @param price
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<AppointSource> queryDoctorSourceAlipay(final int doctorId,
			final int sourceType, final String workDate, final int workType,
			final int organID, final String appointDepartCode,
			final Double price, final String doType) {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		String work = workDate;
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		try {
			// 工作日期
			date = df.parse(work);
		} catch (Exception e) {
			log.error("queryDoctorSourceAlipay() error : "+e);
		}
		return dao.queryDoctorSourceAlipay(doctorId, sourceType, date,
				workType, organID, appointDepartCode, price, doType);
	}

	/**
	 * 查询医生日期剩余号源总数服务(SZ)
	 * 
	 * @param
	 * @return
	 */
	@RpcService
	public List<HashMap<String, Object>> totalByDoctorDateAndWorkDateSz(
			final int doctorId, final int sourceType, final String workDate,
			final int organId, final int isWeek) {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		String work = workDate;
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		try {
			// 工作日期
			date = df.parse(work);
		} catch (Exception e) {
			log.error("totalByDoctorDateAndWorkDateSz() error : "+e);
		}
		return dao.totalByDoctorDateAndWorkDateSz(doctorId, sourceType, date,
				organId, isWeek);
	}

	/**
	 * 服务名:查询医生分时段号源服务(SZ)
	 * 
	 * @author xyf
	 * @param doctorId
	 * @param sourceType
	 * @param workDate
	 * @param workType
	 * @param organID
	 * @param price
	 * @return
	 * @throws DAOException
	 */
	@RpcService
	public List<AppointSource> queryDoctorSourceAlipaySz(final int doctorId,
			final int sourceType, final String workDate, final int workType,
			final int organID, final Double price, final String doType) {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		String work = workDate;
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		try {
			// 工作日期
			date = df.parse(work);
		} catch (Exception e) {
			log.error("queryDoctorSourceAlipaySz() error : "+e);
		}
		return dao.queryDoctorSourceAlipaySz(doctorId, sourceType, date,
				workType, organID, price, doType);
	}

	/**
	 * 获取邵逸夫挂号科室下的医生列表
	 * 
	 * @param
	 * @return
	 */
	@RpcService
	public List<Doctor> queryDoctorList(final int bussType,
			final Integer department, final int organId) {
		QueryDoctorListDAO dao = DAOFactory.getDAO(QueryDoctorListDAO.class);
		return dao.queryDoctorList(bussType, department, organId);
	}

	/**
	 * 号源重复时重新获取可用号源
	 * 
	 * @param
	 * @return
	 */
	@RpcService
	public HashMap<String, Object> doSourceByErrCodeAndSourceId(
			final int errCode, final int appointSourceId) {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		return dao.doSourceByErrCodeAndSourceId(errCode, appointSourceId);
	}

	/**
	 * 获取邵逸夫挂号科室下的医生列表(支付宝)
	 * 
	 * @param
	 * @return
	 */
	@RpcService
	public List<Doctor> queryDoctorListAlipay(final String appointDepartCode,
			final int organId) {
		QueryDoctorListDAO dao = DAOFactory.getDAO(QueryDoctorListDAO.class);
		return dao.queryDoctorListAlipay(appointDepartCode, organId);
	}

	/**
	 * 查询某个医生一周内是否有号源(邵逸夫支付宝)
	 * 
	 * @param
	 * @return
	 */
	@RpcService
	public boolean checkDoctorSourceForWeek(final int doctorId,
			final String startDay, final int organId,
			final String appointDepartCode) {
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		String work = startDay;
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		try {
			// 起始时间
			date = df.parse(work);
		} catch (Exception e) {
			log.error("checkDoctorSourceForWeek() error : "+e);
		}
		Date endDay = DateConversion.getDateOfWeekNow(date);
		return dao.checkDoctorSourceForWeek(doctorId, date, endDay, organId,
				appointDepartCode);
	}
	@RpcService
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<DoctorDateSource> totalByDoctorDateAndCloudClinicAlipay(
			final int doctorId, final int sourceType){
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		return dao.totalByDoctorDateAndCloudClinicAlipay(doctorId, sourceType);
	}

	@RpcService
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public	List<AppointSource> queryDoctorSource(final int doctorId,
										  final int sourceType, final Date workDate, final int workType,
										  final int organID, final String appointDepartCode,
										  final Double price){
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		return dao.queryDoctorSource(doctorId,sourceType,workDate,workType,organID,appointDepartCode,price);
	}
}
