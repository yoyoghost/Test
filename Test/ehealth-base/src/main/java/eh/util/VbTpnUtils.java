package eh.util;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eh.vb.tpn.VbModelParam;
import eh.vb.tpn.VbModelResult;

/**
 * 
 * TPN(肠外营养配置方案)
 * 
 * @author hexy
 *
 */
public class VbTpnUtils {

	private static Double m;

	private static Double k;

	private static Double a;

	private static Double b;

	private static Double c;

	private static final Logger logger = LoggerFactory
			.getLogger(VbTpnUtils.class);

	public static VbModelResult calculationTpnScheme(VbModelParam param) {
		boolean flag = checkParam(param);
		if (!flag)
			throw new IllegalArgumentException(
					"the param is null or this attribute is null or be equal to zero please check.");
		// 返回模型
		VbModelResult modelResult = new VbModelResult();
		// 体重赋值
		m = Double.valueOf(param.getWeight());
		// 热卡赋值
		k = Double.valueOf(param.getCalorie());

		Double n = Double.valueOf(param.getSugar());

		Double l = Double.valueOf(param.getFat());

		try {
			// 计算葡萄糖注射液
			calculationGlucoseSolution(modelResult, n,l);
			// 计算脂肪乳针
			calculationFatEmulsion(modelResult, l);
			// 计算复方氨基酸针0
			calculationAminoAcid(modelResult);
			// 计算液体总量
			calculationSum(modelResult,n,l);
		} catch (Exception e) {
			logger.error("calculationTpnScheme exception message error info "
					+ e.getMessage());
		}

		return modelResult;
	}

	private static boolean checkParam(VbModelParam param) {
		if (null == param) {
			return false;
		} else if (StringUtils.isBlank(param.getWeight())) {
			return false;
		} else if (StringUtils.isBlank(param.getCalorie())) {
			return false;
		} else if (StringUtils.isBlank(param.getFat())) {
			return false;
		} else if (StringUtils.isBlank(param.getSugar())) {
			return false;
		} else if (null == param.getDoctor()) {
			return false;
		}
		return true;
	}

	private static void calculationGlucoseSolution(VbModelResult modelResult,
			Double n, Double l) {
		double fat = l * 10;
		modelResult.setGlucoseInjection_Label6(Math.floor(Math.floor(k * m * n
				/ fat) / 50) * 100); // '优先选择“葡萄糖注射液50%，100mL”的计算体积值
		a = Math.floor(k * m * n / fat)
				- (Math.floor(Math.floor(k * m * n / fat) / 50) * 50) - 25;
		if (a > 0) {
			// 葡萄糖注射液 10%，500mL”的计算体积值
			modelResult.setGlucoseInjection_Label7((a + 25) / 50 * 500);
		} else {
			// 葡萄糖注射液 10%，250mL”的计算体积值		
			modelResult.setGlucoseInjection_Label8((a + 25) / 25 * 250);		
		}
	}

	private static void calculationFatEmulsion(VbModelResult modelResult,
			Double l) {
		modelResult
				.setFatEmulsion_Label15((Math.floor(k * m * l / 4500) * 250)); // 优先预选择“结构脂肪乳针
																				// 250mL:50g”的计算体积值
		b = (k * m * l / 4500) - 1; // 去除“结构脂肪乳针 250mL:50g ”的脂肪量的值后剩余的脂肪量的值-1
									// 赋值给b
		if (b < 0) {
			modelResult.setFatEmulsion_Label15(Math
					.floor((k * m * l / 4500) * 250));
		} else {
			modelResult
					.setFatEmulsion_Label15(Math.floor(k * m * l / 4500) * 250);
			modelResult
					.setFatEmulsion_Label12(Math
							.floor(((k * m * l / 4500) - Math.floor(k * m * l
									/ 4500)) * 100)); // “脂肪乳剂 20%，100ml”的计算体积值
		}

	}

	private static void calculationAminoAcid(VbModelResult modelResult) {
		double count = (k * m / 120) * 6.25 / 50;
		if (count < 1) {
			double label16 = Math.floor(count * 500);
			modelResult.setAnping_Label16(label16);
		} else {
			double label16 = Math.floor(count) * 500;
			modelResult.setAnping_Label16(label16);
			c = (k * m / 120) * 6.25 - label16 / 10;
			if (c < 10) {
				double label18 = Math
						.floor((((k * m / 120) * 6.25 - label16 / 10) / 10) * 50);
				modelResult.setDortmundNeedle_Label18(label18);
			} else {
				double label17 = Math
						.floor((((k * m / 120) * 6.25 - label16 / 10) / 20) * 100);
				modelResult.setForceInjection_Label17(label17);
			}
		}
	}

	private static void calculationSum(VbModelResult modelResult, Double n, Double l) {
		
		double label26 = Math
				.floor(Math.floor(Math.floor(k * m * n / (l*10)) / 10) / 2) * 2;
		modelResult.setInsulin_Label26(label26); // 胰岛素针量的计算
		// 初步估计总的液体量
		modelResult.setEstimateLiquidSum_129(72
				+ modelResult.getGlucoseInjection_Label6()
				+ modelResult.getGlucoseInjection_Label7()
				+ modelResult.getGlucoseInjection_Label8()
				+ modelResult.getGlucoseInjection_Label9()
				+ modelResult.getGlucoseInjection_Label10()
				+ modelResult.getFatEmulsion_Label11()
				+ modelResult.getFatEmulsion_Label12()
				+ modelResult.getFatEmulsion_Label13()
				+ modelResult.getFatEmulsion_Label14()
				+ modelResult.getFatEmulsion_Label15()
				+ modelResult.getAnping_Label16()
				+ modelResult.getForceInjection_Label17()
				+ modelResult.getDortmundNeedle_Label18()
				+ modelResult.getDortmundNeedle_Label19());
		// 根据总的液体量计算"氯化钾注射液 10%，10ml " 的计算体积
		double label21 = Math
				.floor(modelResult.getEstimateLiquidSum_129() / 500) * 15;
		modelResult.setPci_Label21(label21);
		// 计算总的液体量
		modelResult.setCalculationLiquidSum_129(72
				+ modelResult.getGlucoseInjection_Label6()
				+ modelResult.getGlucoseInjection_Label7()
				+ modelResult.getGlucoseInjection_Label8()
				+ modelResult.getGlucoseInjection_Label9()
				+ modelResult.getGlucoseInjection_Label10()
				+ modelResult.getFatEmulsion_Label11()
				+ modelResult.getFatEmulsion_Label12()
				+ modelResult.getFatEmulsion_Label13()
				+ modelResult.getFatEmulsion_Label14()
				+ modelResult.getFatEmulsion_Label15()
				+ modelResult.getAnping_Label16()
				+ modelResult.getForceInjection_Label17()
				+ modelResult.getDortmundNeedle_Label18()
				+ modelResult.getDortmundNeedle_Label19()
				+ modelResult.getPci_Label21());

	}

}
