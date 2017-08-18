package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import eh.account.constant.AccountConstant;
import eh.base.constant.OrganConstant;
import eh.base.dao.*;
import eh.bus.dao.*;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorAccountDetail;
import eh.entity.base.Organ;
import eh.entity.base.ServerPrice;
import eh.entity.bus.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DoctorAccountService {
	public static final Logger log = Logger
			.getLogger(DoctorAccountService.class);

	/**
	 * 获取奖励的serverId
	 * 
	 * @desc 医生之间发起的业务，给医生的业务奖励
	 * @author ZX
	 * @date 2015-4-26 下午6:10:32
	 * @param doctorId
	 *            医生序号
	 * @param serverId
	 *            服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方)29咨询30关注31专家解读完成收入
	 *            32寻医问药完成收入)
	 * @param BussId
	 *            业务序号（预约、咨询、转诊、会诊等业务单的序号）
	 * @param addFlag
	 *            是否追加奖励 (1：是；0：否)
	 */
	public Integer addDoctorIncome(final int doctorId, int serverId,
			final int bussId, final int addFlag) {
		Integer newServerId=null;
		
		// 判断所属机构是否奖励
		if (!getOrganRewardFlag(serverId, bussId, doctorId)) {
			return newServerId;
		}
		
		// 判断是否为新用户
		if (isNewUser(doctorId, serverId, bussId)) {
			if (serverId == 2 && isSamePersonForTransfer(bussId)) {
				return null;
			}
			newServerId = getNewServerId(serverId);
		} else {

			if (!canAddMoney(doctorId, serverId, bussId, addFlag)) {
				return null;
			}

			newServerId = getNewServerId(serverId, doctorId, bussId);
		}

		return newServerId;
	}

	/**
	 * 判断业务是否为同等级医院发起的
	 *
	 * @author ZX
	 * @date 2015-7-26 下午4:25:48
	 * @param doctorId
	 * @param serverId
	 *           服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方))
	 * @param bussId
	 *            业务序号（预约、咨询、转诊、会诊等业务单的序号）
	 * @param doctorId
	 *            医生id
	 * @return true:同级间业务;false:不同级间业务
	 */
	public Boolean isSameGradeOrgan(int serverId, int bussId, int doctorId) {
		TransferDAO transferDao = DAOFactory.getDAO(TransferDAO.class);
		AppointRecordDAO recordDao = DAOFactory.getDAO(AppointRecordDAO.class);
		MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
		EndMeetClinicDAO meetClinicRecordDAO = DAOFactory
				.getDAO(EndMeetClinicDAO.class);

		Boolean isSameGradeOrgan =false;
		Integer reqOrgan;
		Integer tarOrgan;

		switch (serverId) {
			case 1:
			case 2:
				// 转诊申请
				Transfer reqTransfer = transferDao.get(bussId);
				reqOrgan = reqTransfer.getRequestOrgan();
				tarOrgan = reqTransfer.getTargetOrgan();
				isSameGradeOrgan=isSameGrade(reqOrgan,tarOrgan);
				break;
			case 3:
				// 会诊申请
				MeetClinic meetClinic = meetClinicDAO.get(bussId);
				reqOrgan = meetClinic.getRequestOrgan();

				// 会诊处理单
				List<MeetClinicResult> resultList = meetClinicRecordDAO
						.findByMeetClinicId(bussId);

				List<Integer> tarOrgans=new ArrayList<Integer>();
				// 循环判断是否为同等级
				for (MeetClinicResult meetClinicResult : resultList) {

					Integer targetOrgan=meetClinicResult.getTargetOrgan();
					if (meetClinicResult.getExeStatus() == 2
							&&  targetOrgan!= null) {
						tarOrgans.add(targetOrgan);
					}

				}

				isSameGradeOrgan=isSameGrade(reqOrgan,tarOrgans);
				break;

			case 4:
				// 会诊接收
				List<MeetClinicResult> list = meetClinicRecordDAO
						.findByExeDoctorAndMeetClinicId(doctorId, bussId);
				if (list.size() > 0) {
					MeetClinicResult result = list.get(0);
					tarOrgan = result.getExeOrgan();

					MeetClinic clinic = meetClinicDAO.get(result.getMeetClinicId());
					reqOrgan = clinic.getRequestOrgan();
					isSameGradeOrgan=isSameGrade(reqOrgan,tarOrgan);
				}
				break;
		}

		return isSameGradeOrgan;
	}

	/**
	 * 获取实际的serverPrice
	 * @param newServerId
	 * @param bussId
	 * @param addFlag
	 * @param isSameGradeOrgan true:同级间业务;false:不同级间业务,默认为不同级)
     * @return
     */
	public ServerPrice getActualServerPrice(Integer newServerId,int bussId,int doctorId,int addFlag,Boolean isSameGradeOrgan ){
		// 查询服务价格
		ServerPriceDAO serverPriceDAO = DAOFactory
				.getDAO(ServerPriceDAO.class);
		ServerPrice serverPrice = serverPriceDAO
				.getByServerId(newServerId);
		if (serverPrice == null) {
//			log.error("ServerPrice["+newServerId+"]，bussId["+bussId+"],addFlag["+addFlag+"]奖励失败");
			throw new DAOException(404, "ServerPrice[" + newServerId
					+ "] not exist");
		}

		BigDecimal price = new BigDecimal(0d);
		//设置的价格大于0,属于奖励金额
		if(serverPrice.getPrice().compareTo(BigDecimal.ZERO)>=0){
			price=getDetailPrice(serverPrice,addFlag,isSameGradeOrgan );
		}

		//设置的价格小于0,属于扣费金额
		if(serverPrice.getPrice().compareTo(BigDecimal.ZERO)<0){
			price=getDetailPrice(newServerId,  bussId,  doctorId);
		}

		serverPrice.setPrice(price);

		return serverPrice;
	}

	/**
	 * 获取奖励时的价格
	 * @param serverId
	 * @param bussId
	 * @param doctorId
	 * @return
	 */
	private BigDecimal getDetailPrice(ServerPrice serverPrice,int addFlag,Boolean isSameGradeOrgan ) {

		//不等级机构发起的业务，使用价格diffPrice,默认不同级
		if(!isSameGradeOrgan){
			serverPrice.setPrice(serverPrice.getDiffPrice());
		}

		// 计算此次收入
		BigDecimal price = new BigDecimal(0d);
		if (addFlag == 1) {
			price = serverPrice.getPrice().add(
					serverPrice.getAddPrice());
		} else {
			price = serverPrice.getPrice();
		}

		return price;
	}

	/**
	 * 预约取消时，先获取奖励过的奖励详情单奖励价格(负值)
	 * @param serverId
	 * @param bussId
	 * @param doctorId
     * @return
     */
	private BigDecimal getDetailPrice(int serverId, int bussId, int doctorId) {
		BigDecimal price=new BigDecimal(0);

		DoctorAccountDetailDAO detailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
		AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);

		AppointRecord record = appointDao.getByAppointRecordId(bussId);


		int bussType=0;// 1转诊；2会诊；3咨询；4预约;5远程预约
		int bussnessId=bussId;

		if(serverId==7 || serverId==15 || serverId==18){
			// 普通预约取消
			if (record != null && record.getAppointRoad() == 5) {
				bussType=4;
				bussnessId=bussId;

				// 转诊预约(给申请医生扣费)
			} else if (record != null && record.getAppointRoad() == 6) {
				bussType=1;
				bussnessId=record.getTransferId();
			}
		}

		if(serverId==24 || serverId==26){
			bussType = 5;
		}

		DoctorAccountDetail detail = detailDAO.getByBussTypeAndBussId(
				bussType, bussnessId, doctorId);

		//获取之前的奖励金额
		if (detail != null) {
			price=detail.getMoney();
		}

		return price.multiply(new BigDecimal(-1));
	}

	/**
	 * 判断所属机构是否奖励
	 * 
	 * @author zhangx
	 * @date 2016-3-13 下午3:41:42
	 * @param serverId
	 * @return true奖励;false不奖励
	 */
	private Boolean getOrganRewardFlag(int serverId, int bussId, int doctorId) {
		Boolean organRewardFlag = true;


		TransferDAO transferDao = DAOFactory.getDAO(TransferDAO.class);
		AppointRecordDAO recordDao = DAOFactory.getDAO(AppointRecordDAO.class);
		MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
		EndMeetClinicDAO endMeetClinicDAO = DAOFactory
				.getDAO(EndMeetClinicDAO.class);
		OrganConfigDAO configDao=DAOFactory.getDAO(OrganConfigDAO.class);

		List<Integer> noIncomeOrgans=configDao.findOrganIdByAccountFlag(0);

		Integer organId = null;

		switch (serverId) {
		case 1:
			// 转诊申请
			Transfer reqTransfer = transferDao.get(bussId);
			organId = reqTransfer.getRequestOrgan();
			break;
		case 2:
			// 转诊接收
			Transfer tarTransfer = transferDao.get(bussId);
			organId = tarTransfer.getTargetOrgan();
			break;
		case 3:
			// 会诊申请
			MeetClinic meetClinic = meetClinicDAO.get(bussId);
			organId = meetClinic.getRequestOrgan();
			break;
		case 4:
			// 会诊接收
			List<MeetClinicResult> list = endMeetClinicDAO
					.findByExeDoctorAndMeetClinicId(doctorId, bussId);
			if (list.size() > 0) {
				MeetClinicResult result = list.get(0);
				organId = result.getExeOrgan();
			}
			break;
		case 6:
		case 23:
				// 预约服务
				AppointRecord reqRecord = recordDao.get(bussId);
				if (!StringUtils.isEmpty(reqRecord.getAppointOragn())) {
					organId = Integer.parseInt(reqRecord.getAppointOragn());
				}
				break;
		case 7:
		case 24:
			// 预约取消
			AppointRecord cancelRecord = recordDao.get(bussId);
			if (cancelRecord.getAppointRoad() == 5) {
				organId = Integer.parseInt(cancelRecord.getAppointOragn());
			} else {

				Transfer t = transferDao.getById(cancelRecord.getTransferId());
				organId = t.getRequestOrgan();
			}
			break;
		case 27:
		case 28:
			// 预约云门诊(接诊方/出诊方)
			AppointRecord cloudAppoint = recordDao.get(bussId);
			organId = cloudAppoint.getOrganId();
			break;
		}

		if (organId == null) {
			organRewardFlag = false;
		} else {
			if (noIncomeOrgans.contains(organId)) {
				organRewardFlag = false;
			}
		}

		return organRewardFlag;
	}

	/**
	 * 根据 医生id 和 业务类型 判断是否为新用户
	 * 
	 * @author ZX
	 * @date 2015-9-7 下午6:54:56
	 * @param doctorId
	 *            医生id
	 * @param serverId
	 *            服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方))
	 * @param serverId
	 *            业务单号
	 * @return 是新用户:true;不是新用户:false
	 */
	private Boolean isNewUser(int doctorId, int serverId, int bussId) {
		Boolean b = true;

		AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
		MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
		EndMeetClinicDAO endMeetClinicDAO = DAOFactory
				.getDAO(EndMeetClinicDAO.class);
		ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
		DoctorAccountDetailDAO detailDao = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);
		TransferDAO transDao = DAOFactory.getDAO(TransferDAO.class);
		DoctorAccountDetailDAO detailDAO = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);

		switch (serverId) {
		case 1:
		case 2:
		case 8:
		case 10:
			// 转诊接收后，会往预约表里插入一条预约记录，预约成功，则两张表记录都为成功；预约失败,则预约记录为失败，转诊记录为取消。
			// 预约失败
			long successTransferNum=appointDao.getSuccessTransferNum(doctorId);


			//2016-08-01:转诊接收成功，申请方+接收方加钱，取消扣申请方的钱，预约记录，转诊记录都不为有效
			// 加号转诊接收成功数量
			long virtualTransferNum = transDao.getVirtualTransferNum(doctorId);

			long num = detailDAO.getAllNumForDoctor(doctorId, 1, 10, 1);// 首单接收奖励

			if (successTransferNum + virtualTransferNum > 1 || num>=1) {
				b = false;
			}
			break;

		case 3:
		case 4:
		case 11:
		case 12:
		case 16:
			// 会诊
			List<MeetClinic> meetList = meetClinicDAO
					.findByRequestDoctorAndMeetClinicStatus(doctorId, 2);
			List<MeetClinicResult> resultList = endMeetClinicDAO
					.findByExeDoctorAndExeStatus(doctorId, 2);

			if (meetList.size() + resultList.size() > 1) {
				b = false;
			}
			break;

		case 5:
		case 13:
			// 咨询
			List<Consult> consultList = consultDAO
					.findByExeDoctorAndConsultStatus(doctorId, 2);
			if (consultList.size() > 1) {
				b = false;
			}
			break;

		case 6:
		case 14:
			// 预约
			List<AppointRecord> appoList = appointDao
					.findAppointRecordByAppointUserAndAppointStatus(doctorId
							+ "", 5, 0);
			if (appoList.size() > 1) {
				b = false;
			}
			break;
		case 23:
			//预约远程云门诊
			int road = 5;// 5医生诊间预约;6转诊预约
			int telClinicFlag=1;//0线下；1预约云门诊；2在线云门诊

			List<AppointRecord> cloudAppoList = appointDao
					.findCloudAppointRecordByAppointUserAndAppointStatus(telClinicFlag,doctorId
							+ "", road, 0);

			long num3 = detailDAO.getAllNumForDoctor(doctorId, 5, 25, 1);// 首次奖励
			long num4 = detailDAO.getAllNumForDoctor(doctorId, 5,26, 1);// 首次奖励取消


			if (cloudAppoList.size()/2 > 1 || num3-num4==1) {
				b = false;
			}
			break;
		case 7:
			// 预约取消
			AppointRecord record = appointDao.getByAppointRecordId(bussId);

			// 普通预约取消
			if (record != null && record.getAppointRoad() == 5) {
				int bussType = 4;// 1转诊；2会诊；3咨询；4预约;5远程预约
				DoctorAccountDetail detail = detailDao.getByBussTypeAndBussId(
						bussType, bussId, doctorId);
				if (detail == null) {
					b = false;
				}
				if (detail != null && detail.getServerId() != null
						&& detail.getServerId() == 6) {
					b = false;
				}

				// 转诊预约(给申请医生扣费)
			} else if (record != null && record.getAppointRoad() == 6) {
				int bussType = 1;// 1转诊；2会诊；3咨询；4预约;5远程预约
				DoctorAccountDetail detail = detailDao.getByBussTypeAndBussId(
						bussType, record.getTransferId(), doctorId);

				// 未加费用
				if (detail == null) {
					b = false;
				}

				// 转诊申请
				if (detail != null && detail.getServerId() != null
						&& detail.getServerId() == 1) {
					b = false;
				}

				// 转诊接收(转诊给自己)
				if (detail != null && detail.getServerId() != null
						&& detail.getServerId() == 2) {
					b = false;
				}

			} else {
				b = false;
			}
			break;
		case 24:
			// 预约取消
			AppointRecord cloudRecord = appointDao.getByAppointRecordId(bussId);
			int bussType = 5;// 1转诊；2会诊；3咨询；4预约;5远程预约
			DoctorAccountDetail detail = detailDao.getByBussTypeAndBussId(
					bussType, bussId, doctorId);
			if (detail == null) {
				b = false;
			}
			if (detail != null && detail.getServerId() != null
					&& detail.getServerId() == 23) {
				b = false;
			}
			break;
		default:
			b = false;
			break;
		}

		return b;
	}

	/**
	 * 转诊判断是否为同一个人
	 * 
	 * @author zhangx
	 * @date 2015-10-20下午7:37:04
	 * @param bussId
	 *            转诊单号
	 * @return true：是；false：否
	 * 
	 * @date 2015-10-20 下午7:34:07 zhangx 由于发现云门诊转诊接收的时候会往预约记录表中插入两条数据，因此，
	 *       将此方法isSamePerson修改为isSamePersonForTransfer
	 */
	private Boolean isSamePersonForTransfer(int bussId) {
		Boolean b = true;

		TransferDAO transDao = DAOFactory.getDAO(TransferDAO.class);
		Transfer trans = transDao.getById(bussId);
		if (trans != null && trans.getRequestDoctor() != null
				&& trans.getConfirmDoctor() != null) {
			if (trans.getRequestDoctor().intValue() != trans.getConfirmDoctor()
					.intValue()) {
				b = false;
			}
		}
		return b;
	}

	/**
	 * 获取新的serverId
	 * 
	 * @author ZX
	 * @date 2015-9-7 下午7:22:28
	 * @param serverId
	 *            服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方))
	 * @return
	 */
	private int getNewServerId(int serverId) {
		switch (serverId) {
		case 1:
			serverId = 8;
			break;
		case 2:
			serverId = 10;
			break;
		case 3:
			serverId = 11;
			break;
		case 4:
			serverId = 12;
			break;
		case 5:
			serverId = 13;
			break;
		case 6:
			serverId = 14;
			break;
		case 7:
			serverId = 15;
			break;
		case 23:
			serverId = 25;
			break;
		case 24:
			serverId = 26;
			break;
		}
		return serverId;
	}
	/**
	 * 获取新的serverId
	 *
	 * @author ZX
	 * @date 2015-9-7 下午7:22:28
	 * @param serverId
	 *            服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方))
	 * @return
	 */
	private int getNewServerId(int serverId, int doctorId, int bussId) {
		DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
		DoctorAccountDetailDAO detailDAO = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);
		AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);

		if (serverId == 4) {
			Doctor doc = docDao.getByDoctorId(doctorId);
			if (doc != null
					&& doc.getProTitle() != null
					&& (doc.getProTitle().equals("1") || doc.getProTitle()
							.equals("2"))) {
				serverId = 16;
			} else {
				serverId = 4;
			}
		}

		if (serverId == 7) {
			// 预约取消
			AppointRecord record = appointDao.getByAppointRecordId(bussId);

			// 普通预约取消
			if (record != null && record.getAppointRoad() == 5) {
				int bussType = 4;// 1转诊；2会诊；3咨询；4预约;5远程预约
				DoctorAccountDetail detail = detailDAO.getByBussTypeAndBussId(
						bussType, bussId, doctorId);
				if (detail != null) {
					serverId = 7;
				}

				// 转诊预约(给申请医生扣费)
			} else if (record != null && record.getAppointRoad() == 6) {
				int bussType = 1;// 1转诊；2会诊；3咨询；4预约;5远程预约
				int transferId = record.getTransferId();
				DoctorAccountDetail detail = detailDAO.getByBussTypeAndBussId(
						bussType, transferId, doctorId);
				if (detail != null && detail.getServerId() != null) {
					if (detail.getServerId() == 1) {
						serverId = 7;
					} else if (detail.getServerId() == 2) {
						serverId = 18;
					}
				}

			}
		}

		return serverId;
	}

	/**
	 * 是否能增加账户收入,(账户收入控制)
	 * 
	 * @author ZX
	 * @date 2015-4-26 下午6:10:32
	 * @param doctorId
	 *            医生序号
	 * @param serverId
	 *            服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方))
	 * @param BussId
	 *            业务序号（预约、咨询、转诊、会诊等业务单的序号）
	 * @param addFlag
	 *            是否追加奖励 (1：是；0：否)
	 *
	 * @desc 2016-07-26 云门诊申请方,接诊方不限次数
	 */
	private Boolean canAddMoney(int doctorId, int serverId, int bussId,
			int addFlag) {

		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		AppointRecordDAO appointDAO = DAOFactory.getDAO(AppointRecordDAO.class);
		DoctorAccountDetailDAO detailDAO = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);

		Boolean canAddMoney = false;

		// 判断奖励医生是否存在
		Doctor docInfo = doctorDAO.getByDoctorId(doctorId);
		if (docInfo == null) {
			log.error("奖励业务[" + serverId + "-" + bussId + "]，找不到奖励医生"
					+ doctorId);
			return canAddMoney;
		}

		// 会诊申请--同院之间会诊，申请不加钱；不同院申请，首次
		if (serverId == 3) {
			int bussType = 2;// 1转诊；2会诊；3咨询；4预约;5远程预约

			Boolean isSameOrgan = isSameOrgan(serverId, bussId, doctorId);
			Boolean chiefCanAddMoney = chiefCanAddMoney(doctorId, serverId,
					bussId, bussType);

			if (!isSameOrgan && chiefCanAddMoney) {
				canAddMoney = true;
			}

			// 会诊处理--首个回复的,不同院间会诊,首席医生不限制，非首席医生首次
		} else if (serverId == 4) {
			int bussType = 2;// 1转诊；2会诊；3咨询；4预约;5远程预约
			Boolean isSameOrgan = isSameOrgan(serverId, bussId, doctorId);
			Boolean chiefCanAddMoney = chiefCanAddMoney(doctorId, serverId,
					bussId, bussType);
			Boolean isFirstMeetClinicDoctor = isFirstMeetClinicDoctor(doctorId,
					bussId);
			if (isFirstMeetClinicDoctor && !isSameOrgan && chiefCanAddMoney) {
				canAddMoney = true;
			}
			// 预约服务--发起方每日首单
		} else if (serverId == 6) {
			int bussType = 4;// 1转诊；2会诊；3咨询；4预约;5远程预约
			canAddMoney = chiefCanAddMoney(doctorId, serverId, bussId, bussType);

		// 远程预约服务--发起方不限次数
		}else if(serverId == 23){
			int bussType = 5;// 1转诊；2会诊；3咨询；4预约;5远程预约
//			canAddMoney =chiefCanAddMoney(doctorId, serverId, bussId, bussType);//每日一次


			canAddMoney=true;
			AppointRecord cloudRecord = appointDAO.getByAppointRecordId(bussId);
			String telClinicId=cloudRecord.getTelClinicId();
			List<AppointRecord> records=appointDAO.findByTelClinicId(telClinicId);
			for (AppointRecord r:records) {
				Integer id=r.getAppointRecordId();
				DoctorAccountDetail detail = detailDAO.getByBussTypeAndBussId(
						bussType, id, doctorId);
				if (detail != null) {
					canAddMoney = false;
				}
			}



			//预约取消
		}else if (serverId == 7) {
			int bussType = 4;// 1转诊；2会诊；3咨询；4预约;5远程预约
			canAddMoney = chiefCanAddMoney(doctorId, serverId, bussId, bussType);

			//远程预约取消服务--不限次数，按实际的来
		} else if(serverId == 24){
			int bussType = 5;// 1转诊；2会诊；3咨询；4预约;5远程预约
			canAddMoney = chiefCanAddMoney(doctorId, serverId, bussId, bussType);

			//远程门诊预约服务(接诊方)-不限次数，按实际的来
		}else if(serverId == 27){
			int bussType = 5;// 1转诊；2会诊；3咨询；4预约;5远程预约
			canAddMoney = true;//chiefCanAddMoney(doctorId, serverId, bussId, bussType);

			//远程门诊预约服务(出诊方))
		}else if(serverId == 28){
			int bussType = 5;// 1转诊；2会诊；3咨询；4预约;5远程预约
			canAddMoney = chiefCanAddMoney(doctorId, serverId, bussId, bussType);

			// 转诊申请
		}else if (serverId == 1) {
			int bussType = 1;// 1转诊；2会诊；3咨询；4预约;5远程预约
			Boolean chiefCanAddMoney = chiefCanAddMoney(doctorId, serverId,
					bussId, bussType);

			Boolean b = isSamePersonForTransfer(bussId);

			// 首次奖励 并且不为同一个人
			if (chiefCanAddMoney && !b) {
				canAddMoney = true;
			}

			// 转诊接收(首席医生不限制，非首席医生首次)
		} else if (serverId == 2) {
			int bussType = 1;// 1转诊；2会诊；3咨询；4预约;5远程预约
			canAddMoney = chiefCanAddMoney(doctorId, serverId, bussId, bussType);
		} else {
			canAddMoney = true;
		}

		return canAddMoney;

	}

	/**
	 * 是否首次进行奖励
	 * 
	 * @author ZX
	 * @date 2015-7-31 下午4:32:55
	 * @param doctorId
	 * @param serverId
	 *            服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方))
	 * 
	 * @param bussId
	 *            业务序号（预约、咨询、转诊、会诊等业务单的序号）
	 * @param bussType
	 *            1转诊；2会诊；3咨询；4预约;5远程预约
	 * @return true:该奖励;false:不该奖励
	 */
	private Boolean chiefCanAddMoney(int doctorId, int serverId, int bussId,
			int bussType) {
		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		DoctorAccountDetailDAO detailDAO = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);
		AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);

		Boolean chiefCanAddMoney = false;

		// 判断奖励医生是否存在
		Doctor docInfo = doctorDAO.getByDoctorId(doctorId);
		if (docInfo == null) {
			log.error("奖励业务[" + serverId + "-" + bussId + "]，找不到奖励医生"
					+ doctorId);
			return chiefCanAddMoney;
		}

		if (docInfo.getChief() == null) {
			log.error("奖励业务[" + serverId + "-" + bussId + "]，医生[" + doctorId
					+ "]首席标记为null,设置默认为0");
			docInfo.setChief(0);
		}

		// 会诊申请：第一次会诊申请完成加钱(不区分是否为首席医生，也就是说不管是不是首席医生，都是首次申请有奖励)
		if (serverId == 3) {
			long num = detailDAO.getNumForDoctor(doctorId, bussType, serverId,
					1);
			long num2 = detailDAO.getNumForDoctor(doctorId, bussType, 11, 1);
			if (num < 1 && num2 < 1) {
				chiefCanAddMoney = true;
			}
		}

		// 会诊执行：首席医生加钱，不是首席医生第一次完成会诊加钱
		if (serverId == 4) {
			if (docInfo.getChief() == 1) {
				chiefCanAddMoney = true;
			} else {
				long num = detailDAO.getNumForDoctor(doctorId, bussType,
						serverId, 1);
				long num2 = detailDAO
						.getNumForDoctor(doctorId, bussType, 12, 1);
				long num3 = detailDAO
						.getNumForDoctor(doctorId, bussType, 16, 1);
				if (num < 1 && num2 < 1 && num3 < 1) {
					chiefCanAddMoney = true;
				}
			}
		}

		// 预约：第一次完成加钱
		if (serverId == 6) {
			int road = 5;// 5医生诊间预约;6转诊预约

			long num1 = detailDAO.getNumForDoctor(doctorId, bussType, serverId,
					1);// 预约每日首次加钱
			long num2 = detailDAO.getAppointNumForDoctor(doctorId, bussType, 7,
					1, road);// 预约取消
			long num3 = detailDAO.getNumForDoctor(doctorId, bussType, 14, 1);// 首次奖励
			long num4 = detailDAO.getAppointNumForDoctor(doctorId, bussType,
					15, 1, road);// 首次奖励取消

			if (num1 - num2 == 0 && num3 - num4 == 0) {
				chiefCanAddMoney = true;
			}
		}

		// 远程预约申请人：第一次完成加钱
		if (serverId == 23) {
			int road = 5;// 5医生诊间预约;6转诊预约
			int telClinicFlag=1;//0线下；1预约云门诊；2在线云门诊

			long num1 = detailDAO.getNumForDoctor(doctorId, bussType, serverId,
					1);// 预约每日首次加钱
			long num2 = detailDAO.getCloudAppointNumForDoctor(telClinicFlag,doctorId, bussType, 24,
					1, road);// 预约取消
			long num3 = detailDAO.getNumForDoctor(doctorId, bussType, 25, 1);// 首次奖励
			long num4 = detailDAO.getCloudAppointNumForDoctor(telClinicFlag,doctorId, bussType,
					26, 1, road);// 首次奖励取消

			if (num1 - num2 == 0 && num3 - num4 == 0) {
				chiefCanAddMoney = true;
			}
		}

		// 转诊申请：每日首单有奖励
		if (serverId == 1) {
			int road = 6;// 5医生诊间预约;6转诊预约
			long num1 = detailDAO.getNumForDoctor(doctorId, bussType, serverId,
					1);// 预约每日首次加钱
			long num2 = detailDAO.getAppointNumForDoctor(doctorId, 4, 7, 1,
					road);// 预约取消
			long num3 = detailDAO.getNumForDoctor(doctorId, bussType, 8, 1);// 首次奖励
			long num4 = detailDAO.getAppointNumForDoctor(doctorId, 4, 15, 1,
					road);// 首次奖励取消

			if (num1 - num2 == 0 && num3 - num4 == 0) {
				chiefCanAddMoney = true;
			}
		}

		// 预约取消：先判断被取消的那笔预约单是否为奖励的预约单
		if (serverId == 7) {
			// 预约取消
			AppointRecord record = appointDao.getByAppointRecordId(bussId);

			// 普通预约取消
			if (record != null && record.getAppointRoad() == 5) {
				bussType = 4;// 1转诊；2会诊；3咨询；4预约;5远程预约
				DoctorAccountDetail detail = detailDAO.getByBussTypeAndBussId(
						bussType, bussId, doctorId);
				if (detail != null) {
					chiefCanAddMoney = true;
				}

				// 转诊预约(给申请医生扣费)
			} else if (record != null && record.getAppointRoad() == 6) {
				bussType = 1;// 1转诊；2会诊；3咨询；4预约;5远程预约
				int transferId = record.getTransferId();
				DoctorAccountDetail detail = detailDAO.getByBussTypeAndBussId(
						bussType, transferId, doctorId);
				if (detail != null) {
					chiefCanAddMoney = true;
				}

			}
		}

		// 预约云门诊取消：先判断被取消的那笔预约单是否为奖励的预约单
		if (serverId == 24) {
			// 预约取消
			DoctorAccountDetail detail = detailDAO.getByBussTypeAndBussId(
					bussType, bussId, doctorId);
			if (detail != null) {
				chiefCanAddMoney = true;
			}

		}

		// 预约云门诊(接诊方)
		if (serverId == 27) {
			long num = detailDAO.getNumForDoctor(doctorId,
					bussType, serverId, 1);
			if (num< AccountConstant.CLINIC_OBJECT_ACCEPTS_NUM) {
				chiefCanAddMoney = true;
			}

		}

		// 预约云门诊(出诊方)
		if (serverId == 28) {
			long num = detailDAO.getNumForDoctor(doctorId,
					bussType, serverId, 1);
			if (num< AccountConstant.CLINIC_OBJECT_VISITS_NUM) {
				chiefCanAddMoney = true;
			}

		}

		// 转诊接收
		if (serverId == 2) {
			if (docInfo.getChief() == 1) {
				chiefCanAddMoney = true;
			} else {
				int road = 6;// 5医生诊间预约;6转诊预约

				long num = detailDAO.getNumForDoctor(doctorId, bussType,
						serverId, 1);// 转诊接收
				long num2 = detailDAO
						.getNumForDoctor(doctorId, bussType, 10, 1);// 转诊首次接收

				long num3 = detailDAO.getAppointNumForDoctor(doctorId, 4, 18,
						1, road);// 自己转自己，作为接收方，并且取消

				if (num - num3 < 1 && num2 < 1) {
					chiefCanAddMoney = true;
				}
			}
		}

		return chiefCanAddMoney;
	}

	/**
	 * 判断业务是否为同一家医院发起的
	 * 
	 * @author ZX
	 * @date 2015-7-31 下午4:25:48
	 * @param doctorId
	 * @param serverId
	 *           服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
	 *            转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
	 *            会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
	 *            ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
	 *            25首单奖励;26远程门诊预约取消;27远程门诊预约服务(接诊方)28远程门诊预约服务(出诊方))
	 * @param bussId
	 *            业务序号（预约、咨询、转诊、会诊等业务单的序号）
	 * @param doctorId
	 *            医生id
	 * @return true:同院间会诊;false:不同院间会诊
	 */
	private Boolean isSameOrgan(int serverId, int bussId, int doctorId) {
		MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
		EndMeetClinicDAO meetClinicRecordDAO = DAOFactory
				.getDAO(EndMeetClinicDAO.class);

		Boolean isSameOrgan = true;

		// 会诊申请
		if (serverId == 3) {
			// 会诊申请单
			MeetClinic meetClinic = meetClinicDAO.get(bussId);
			int requestOrgan = meetClinic.getRequestOrgan();

			// 会诊处理单
			List<MeetClinicResult> resultList = meetClinicRecordDAO
					.findByMeetClinicId(bussId);

			// 循环判断是否为同一家医院
			for (MeetClinicResult meetClinicResult : resultList) {
				if (meetClinicResult.getExeStatus() == 2
						&& meetClinicResult.getTargetOrgan() != null
						&& meetClinicResult.getTargetOrgan() != requestOrgan) {
					isSameOrgan = false;
					break;
				}

			}
		}

		if (serverId == 4) {
			// 会诊申请单
			MeetClinic meetClinic = meetClinicDAO.get(bussId);
			int requestOrgan = meetClinic.getRequestOrgan();

			List<MeetClinicResult> list = meetClinicRecordDAO
					.findByExeDoctorAndMeetClinicId(doctorId, bussId);
			// 循环判断是否为同一家医院
			for (MeetClinicResult meetClinicResult : list) {
				if (meetClinicResult.getExeStatus() == 2
						&& meetClinicResult.getTargetOrgan() != null
						&& meetClinicResult.getTargetOrgan() != requestOrgan) {
					isSameOrgan = false;
					break;
				}

			}
		}

		return isSameOrgan;
	}

	/**
	 * 判断该执行单是否为第一条完成的执行单
	 * 
	 * @author ZX
	 * @date 2015-9-9 下午12:57:52
	 * @param meetClinicId
	 * @return true:是第一个回复;false:不是第一个回复
	 */
	private Boolean isFirstMeetClinicDoctor(int doctor, int meetClinicId) {
		Boolean isFirstMeetClinicDoctor = false;

		EndMeetClinicDAO endMeetClinicDAO = DAOFactory
				.getDAO(EndMeetClinicDAO.class);
		List<MeetClinicResult> list = endMeetClinicDAO
				.findByMeetClinicId(meetClinicId);
		// 一对一会诊
		if (list.size() == 1) {
			isFirstMeetClinicDoctor = true;
		}

		if (list.size() > 1) {
			// 判断是否有唯一第一个首个回复
			List<Long> nums = endMeetClinicDAO.findNumByTime(meetClinicId);
			if (nums != null && nums.size() > 0 && nums.get(0) == 1) {
				List<MeetClinicResult> resList = endMeetClinicDAO
						.findByTime(meetClinicId);
				if (resList != null && resList.size() > 0) {
					MeetClinicResult res = resList.get(0);
					if (res.getExeDoctor() != null
							&& res.getExeDoctor().intValue() == doctor) {
						isFirstMeetClinicDoctor = true;
					}
				}

			}
		}

		return isFirstMeetClinicDoctor;
	}




	/**
	 * 判断两个机构是否为同等级，未评级=一级医院
	 * @param reqOrgan 机构1
	 * @param tarOrgan 机构2
     * @return
     */
	private Boolean isSameGrade(Integer reqOrgan,Integer tarOrgan){
		Boolean isSameGrade=false;

		if(reqOrgan.intValue()==tarOrgan.intValue()) {
			isSameGrade = true;
		}else{
			List<Integer> ids=findSameGradeOrganIdByOrgan(reqOrgan);
			if(ids.contains(tarOrgan.intValue())){
				isSameGrade=true;
			}
		}
		return isSameGrade;
	}

	/**
	 * 判断机构是否为同等级，目标机构，有一个不同级，及为不同级，未评级=一级医院
	 * @param reqOrgan 机构1
	 * @param tarOrgan 机构2
	 * @return
	 */
	private Boolean isSameGrade(Integer reqOrgan,List<Integer> tarOrgans){
		Boolean isSameGrade=true;

		List<Integer> ids=findSameGradeOrganIdByOrgan(reqOrgan);
		for (Integer tarOrgan:tarOrgans) {
			if(!ids.contains(tarOrgan.intValue())){
				isSameGrade = false;
				break;
			}
		}
		return isSameGrade;
	}

	/**
	 * 根据机构id获取同等级的机构id，未评级=一级
	 * @param organId
	 * @return
     */
	private List<Integer> findSameGradeOrganIdByOrgan(Integer organId){
		OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
		Organ requestOrgan=organDAO.getByOrganId(organId);
		if(requestOrgan==null){
			requestOrgan=new Organ();
		}

		String grade=StringUtils.isEmpty(requestOrgan.getGrade())?"99":requestOrgan.getGrade();
		List<Integer> ids=new ArrayList<Integer>();

		if(OrganConstant.ORGAN_GRADE_NORATING.equals(grade) || OrganConstant.ORGAN_GRADE_LEVELONE.equals(grade)){
			ids.addAll(organDAO.findOrganIdsByGrade(OrganConstant.ORGAN_GRADE_NORATING));//未评级
			ids.addAll(organDAO.findOrganIdsByGrade(OrganConstant.ORGAN_GRADE_LEVELONE));//一级
		}else{
			ids.addAll( organDAO.findOrganIdsByGrade(grade) );
		}

		return ids;
	}
}
