package eh.base.service;


import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.converter.ConversionUtils;
import eh.account.constant.AccountConstant;
import eh.account.constant.ServerPriceConstant;
import eh.base.dao.DoctorAccountDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganConfigExtDAO;
import eh.base.dao.ServerPriceDAO;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.entity.base.ServerPrice;
import eh.entity.bus.Consult;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class DoctorAccountConsultService {
	public static final Logger logger = Logger
			.getLogger(DoctorAccountConsultService.class);

	/**
	 * 判断咨询补贴时间是否有效
	 * @return true有效；false无效
	 */
	private  Boolean validConsultRewardTime(){
		String consultRewardStart= ParamUtils.getParam(ParameterConstant.KEY_CONSULT_REWARD_STARTTIME, "2017-03-31 00:00:00");
		String consultRewardEnd= ParamUtils.getParam(ParameterConstant.KEY_CONSULT_REWARD_ENDTIME, "");

		Date startTime= ConversionUtils.convert(consultRewardStart, Date.class);
		Date endTime= StringUtils.isEmpty(consultRewardEnd)?null:ConversionUtils.convert(consultRewardEnd, Date.class);

		Date now= new Date();

		if(now.before(startTime)){
			logger.info("当前时间在咨询补贴时间["+consultRewardStart+"]-["+consultRewardEnd+"]之间，不发送咨询补贴");
			return false;
		}

		if(now.after(startTime) && (endTime==null || now.before(endTime))){
			return true;
		}

		logger.info("当前时间["+ JSONUtils.toString(now)+"]在咨询补贴时间["+consultRewardStart+"]-["+consultRewardEnd+"]之间，不发送咨询补贴");
		return false;
	}

	/**
	 * 判断机构是否有咨询补贴
	 * @return true奖励；false不奖励
	 *
	 * wx2.8.1版本，只设置[浙江省地区医院+汉中市中心医院]
	 */
	private Boolean validConsultRewardOrgan(Integer organId){

		Boolean organRewardFlag = true;

		//获取设置不奖励的机构列表
		OrganConfigDAO configDao= DAOFactory.getDAO(OrganConfigDAO.class);
		List<Integer> noIncomeOrgans=configDao.findOrganIdByAccountFlag(AccountConstant.ORGANID_ACCOUNT_FLAG_NO);

		if (organId == null) {
			organRewardFlag = false;
		} else {
			if (noIncomeOrgans.contains(organId)) {
				organRewardFlag = false;
			}
		}

		//个别机构设置咨询是否可奖励(true奖励，false不奖励)
		OrganConfigExtDAO extDao=DAOFactory.getDAO(OrganConfigExtDAO.class);
		Boolean consultRewardFlag=extDao.canConsultSubsidy(organId);

		//机构奖励总开关打开 且 咨询可奖励
		return organRewardFlag && consultRewardFlag;
	}

	/**
	 * 判断一个患者是否存在已完成的[图文+电话]咨询单
	 * @param requestMpi
	 * @return true 存在已完成的[图文+电话]咨询单 false不存在已完成的[图文+电话]咨询单
     */
	private Boolean validConsultRewardNum(String requestMpi,Date endDate){
		ConsultDAO consultDAO=DAOFactory.getDAO(ConsultDAO.class);
		Long num =consultDAO.getEndedOnlineOrAppointConsultByRequestMpi(requestMpi,endDate);

		//咨询单数
		String consultRewardNum= ParamUtils.getParam(ParameterConstant.KEY_CONSULT_REWARD_NUM, "1");
		Integer reqardNum=StringUtils.isEmpty(consultRewardNum)? Integer.valueOf(1):ConversionUtils.convert(consultRewardNum, Integer.class);

		//查询已完成的图文+电话咨询单完成数量
		if(num!=null && num.intValue() <=reqardNum.intValue()){
			return true;
		}

		return  false;
	}

	/**
	 * 计算并获取咨询单最终补贴
	 * @param price
	 * @return
     */
	private BigDecimal getConsultRewardPrice(Double price){
		//咨询比例
		String consultRewardRate= ParamUtils.getParam(ParameterConstant.KEY_CONSULT_REWARD_RATE, "1");
		BigDecimal rate=ConversionUtils.convert(consultRewardRate,BigDecimal.class);

		String consultRewardMax= ParamUtils.getParam(ParameterConstant.KEY_CONSULT_REWARD_MAX, "20");
		BigDecimal max=ConversionUtils.convert(consultRewardMax,BigDecimal.class);

		//获取医生价格和咨询补贴上线的最小值;
		BigDecimal endPrice=new BigDecimal(price).multiply(rate).min(max);

		return endPrice;
	}

	/**
	 * 补贴图文咨询，电话咨询,按咨询单医生设置价格进行1:1补贴，>20则补贴20元
	 * @param consultId
     */
	public void rewardConsult(Integer consultId){
		logger.info("咨询补贴业务单ID="+consultId);

		//判断咨询补贴时间
		Boolean consultRewardTime=validConsultRewardTime();
		if(!consultRewardTime){
			return ;
		}

		ConsultDAO consultDAO=DAOFactory.getDAO(ConsultDAO.class);
		Consult consult=consultDAO.get(consultId);

		if(consult==null || consult.getExeDoctor()==null){
			logger.info("查询不到咨询["+consultId+"],或咨询单exeDoctor为null,不进行奖励");
			return ;
		}

		//判断咨询补贴机构
		Integer organ=consult.getConsultOrgan();
		Boolean consultRewardOrgan=validConsultRewardOrgan(organ);
		if(!consultRewardOrgan){
			logger.info("咨询单["+consultId+"]的机构["+organ+"]设置为不予奖励，不进行奖励");
			return ;
		}

		//判断咨询类型
		Integer requestMode=consult.getRequestMode();
		if(requestMode==null ){
			logger.info("咨询单["+consultId+"]的咨询类型requestMode为null，不进行奖励");
			return ;
		}
		if(!ConsultConstant.CONSULT_TYPE_POHONE.equals(requestMode) &&
				!ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(requestMode) ){
			logger.info("咨询单["+consultId+"]的咨询类型requestMode["+requestMode+"],不为图文咨询，电话咨询，不进行奖励");
			return ;
		}

		//判断患者是否为第一单
		String requestMpi=consult.getRequestMpi();
		if(StringUtils.isEmpty(requestMpi)){
			logger.info("咨询单["+consultId+"]的申请人requestMpi为null或空，不进行奖励");
			return ;
		}
		Boolean consultRewardNum=validConsultRewardNum(requestMpi,consult.getEndDate());
		if(!consultRewardNum){
			logger.info("咨询单["+consultId+"]的申请人["+requestMpi+"]已完成的咨询单超过奖励上限，不进行奖励");
			return ;
		}

		//获取最终奖励金额
		Double price=consult.getConsultPrice()==null?Double.valueOf("0"):consult.getConsultPrice();
		BigDecimal endPrice=getConsultRewardPrice(price);

		//咨询奖励
		Integer serverId = ServerPriceConstant.ID_CONSULT_REWARD;
		Integer doctorId=consult.getExeDoctor();

		// 查询服务价格
		ServerPriceDAO serverPriceDAO = DAOFactory.getDAO(ServerPriceDAO.class);
		final ServerPrice serverPrice = serverPriceDAO.getByServerId(serverId);
		if (serverPrice == null) {
			throw new DAOException(404, "ServerPrice[" + serverId
					+ "] not exist");
		}

		logger.info("咨询补贴业务单["+consultId+"],最终补贴金额["+endPrice+"],补贴对象["+doctorId+"]");

		DoctorAccountDAO doctoraccountdao = DAOFactory.getDAO(DoctorAccountDAO.class);
		doctoraccountdao.addDoctorAccount(doctorId,serverId,consultId,endPrice, serverPrice);
	}
}
