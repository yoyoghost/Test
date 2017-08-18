package eh.base.service;

import com.google.common.collect.Maps;
import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Subscriber;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.account.constant.AccountConstant;
import eh.account.constant.RevenueBusTypeEnum;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.bus.service.DistributeProportionConfigService;
import eh.bus.service.DistributeRecordService;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.AccountInfo;
import eh.entity.base.Doctor;
import eh.entity.base.ServerPrice;
import eh.entity.bus.BusMoneyDistributeRecord;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.log4j.Logger;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public  class DoctorRevenueService {
	public static final Logger log = Logger
			.getLogger(DoctorRevenueService.class);

	/**
	 * 订阅消息，处理数据
	 */
	@PostConstruct
	public void accountMsgConsumer() {
		OnsConfig onsConfig = (OnsConfig) AppContextHolder.getBean("onsConfig");
		if (!onsConfig.onsSwitch) {
			log.info("the onsSwitch is set off, consumer not subscribe.");
			return;
		}
		Subscriber subscriber = MQHelper.getMqSubscriber();
		subscriber.attach(OnsConfig.accountTopic, new ctd.net.broadcast.Observer<AccountInfo>() {
			@Override
			public void onMessage(AccountInfo info) {
				log.info("收到account消息队列："+ JSONUtils.toString(info));

				AccountInfoDAO infoDao=DAOFactory.getDAO(AccountInfoDAO.class);
				//幂等操作,防止重复处理
				if(infoDao.updateStatusToOver(info.getMsgId()) == 0){
					throw new DAOException("reprocessing, abandoned. message " + JSONUtils.toString(info));
				}

				//处理积分
				DoctorRevenueService.addDoctorRevenue(info.getDoctorId(),info.getServerId(),info.getBussId(),info.getCost());
			}
		});
	}


	/**
	 * 增加患者付费的，医生账户收入
	 *
	 * @param doctorId 医生序号
	 * @param serverId 服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *                 转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *                 会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *                 ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *                 25首单奖励;26远程门诊预约取消33专家解读完成收入
	 *            32寻医问药完成收入)
	 * @param bussId   业务序号（预约、咨询、转诊、会诊等业务单的序号）
	 * @param docPrice 业务设置的收入
	 * @author zhangx
	 * @date 2016-1-18 上午10:53:48
	 */
	public static void addDoctorRevenue(final int doctorId, final int serverId,
								 final int bussId, Double docPrice) {
		log.info("DoctorRevenueService.addDoctorRevenue doctorId[" + doctorId + "],ServerPrice[" + serverId + "]，" +
				"bussId[" + bussId + "],docPrice["+docPrice+"]");

		DoctorAccountDAO accountDAO = DAOFactory
				.getDAO(DoctorAccountDAO.class);

		// 查询服务价格
		ServerPriceDAO serverPriceDAO = DAOFactory.getDAO(ServerPriceDAO.class);
		ServerPrice serverPrice = serverPriceDAO.getByServerId(serverId);
		if (serverPrice == null) {
			throw new DAOException(404, "ServerPrice[" + serverId
					+ "] not exist");
		}

		DoctorDAO docDao=DAOFactory.getDAO(DoctorDAO.class);
		Doctor doctor=docDao.get(doctorId);
		if (doctor == null) {
			throw new DAOException(404, "doctor[" + doctorId
					+ "] not exist");
		}

		BigDecimal price=BigDecimal.ZERO;

		if(docPrice!=null){
			price=BigDecimal.valueOf(docPrice);
		}

		//用于检查指定业务是否已经奖励
		DoctorAccountDetailDAO detailDao = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);
		Long num = detailDao.getByServerIdAndBussId(serverId, bussId, doctorId,
				1);
		if (num > 0) {
			log.info("doctorId["+doctorId+"],serverId["+serverId+"],bussId["+bussId+"]," +
					"serverPrice["+serverPrice.getServerId()+"]已给予过积分，不再给予");
			return;
		}

		BigDecimal finalPrice=price;

		//获取业务类型
		RevenueBusTypeEnum revenueEnum=RevenueBusTypeEnum.getByServerId(serverId);
		int subBusType=revenueEnum.getRevenueSubBusType();

		OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
		Integer organId=doctor.getOrgan();
		String organName=organDAO.getNameById(organId);

		//获取分成比例
		Map<String,BigDecimal> rateMap=new DoctorRevenueService()
				.getOrganProportion(String.valueOf(subBusType),organId);

		try{
			if(price.compareTo(BigDecimal.ZERO)>0){
				BigDecimal docRate=rateMap.get(AccountConstant.KEY_RATE_DOCTOR);
				finalPrice=price.multiply(docRate);
			}
			accountDAO.addDoctorAccount(doctorId,serverId,bussId,finalPrice,serverPrice);
		}catch(Exception e){
			log.error(e.getMessage());
		}


		List<BusMoneyDistributeRecord> list=new ArrayList<>();

		Iterator<String> its = rateMap.keySet().iterator();
		while (its.hasNext()) {
			String roleType=its.next();

			BusMoneyDistributeRecord record=new BusMoneyDistributeRecord();
			record.setBusCost(price.doubleValue());
			record.setBusId(bussId);
			record.setBusType(String.valueOf(subBusType));
			record.setRole(roleType);
			record.setProportion(rateMap.get(roleType).doubleValue());

			if(AccountConstant.KEY_RATE_DOCTOR.equals(roleType)){
				record.setRoleId(String.valueOf(doctorId));
				record.setRoleName(doctor.getName());
			}

			if(AccountConstant.KEY_RATE_ORGAN.equals(roleType)){
				record.setRoleId(String.valueOf(organId));
				record.setRoleName(organName);
			}

			if(AccountConstant.KEY_RATE_NGARI.equals(roleType)){
				record.setRoleName(AccountConstant.ROLE_NAME_NGARI);
			}

			list.add(record);
		}

		new DistributeRecordService().saveRecordList(list);
	}


	/**
	 * 按业务类型+机构Id查询分成配置
	 * @param busType
	 * @param organId
	 * @return
	 */
	public Map<String,BigDecimal> getOrganProportion(String busType,
													 Integer organId){
		DistributeProportionConfigService configService =new DistributeProportionConfigService();

		Map<String,BigDecimal> proportionMap=configService.getOrganProportion(busType,organId);
		if(proportionMap==null){
			proportionMap=getDefaultProportion();
		}

		return proportionMap;
	}

	/**
	 * 无任何配置，获取默认分成比例
	 * 医生0.9，平台0.1
	 * @param docPrice
	 * @return
     */
	private Map<String,BigDecimal> getDefaultProportion(){
		Map <String,BigDecimal> rateMap= Maps.newHashMap();

		BigDecimal rate=new BigDecimal(ParamUtils.getParam(ParameterConstant.KEY_DOCACCOUNT_RATE,SystemConstant.rate.toString()));
		rateMap.put(AccountConstant.KEY_RATE_DOCTOR,rate);
		rateMap.put(AccountConstant.KEY_RATE_ORGAN,BigDecimal.ZERO);
		rateMap.put(AccountConstant.KEY_RATE_NGARI,BigDecimal.ONE.subtract(rate));
		return rateMap;
	}

}
