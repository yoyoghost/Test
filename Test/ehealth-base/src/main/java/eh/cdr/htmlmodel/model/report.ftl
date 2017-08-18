<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="description" content="">
<meta name="keywords" content="">
<meta name="apple-mobile-web-app-capable" content="yes"/>
<meta name="apple-mobile-web-app-status-bar-style" content="black"/>
<meta name="format-detection" content="telephone=no"/>
<meta name="format-detection" content="email=no"/>
<meta name="viewport"content="width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=0"/>
<link rel="stylesheet" type="text/css" href="https://a.alipayobjects.com/??amui/dpl/1.0.2/amui.css,amui/dpl/1.0.2/view/city-select.css" media="all" />
<link rel="stylesheet" type="text/css" href="https://a.alipayobjects.com/amui/dpl/1.0.2/widget/loading.css" media="all" />
<script src="https://a.alipayobjects.com/amui/dpl/1.0.2/amui.js">
</script>
</head>
<body>
	<div class="am-header header">
		<h1>检验报告详情</h1>
	</div>
    <div class="height50W100s"></div>
	<div class="am-content" style="margin:0;position:relative;top:10px;" id="main">
		<div class="am-list">
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis am-ft-sm" style="font-weight:100;">项目名称</span>
				<span class="am-ft-ellipsis am-ft-sm" style="font-weight:100;color:#aaa;">${itemName}</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;width:20%">病历号</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;color:#aaa;width:28%">123456</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;font-weight:100;width:20%">姓名</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;color:#aaa;width:20%">${patient.patientName}</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;width:20%">性别</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;color:#aaa;width:28%">${patient.patientSexText}</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;font-weight:100;width:20%">年龄</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;color:#aaa;width:20%">${age}岁</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis am-ft-sm" style="font-weight:100;">检查科室</span>
				<span class="am-ft-ellipsis am-ft-sm" style="font-weight:100;color:#aaa;">${labReport.rePortDepartName}</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;width:20%">检查医生</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;color:#aaa;width:28%">${labReport.rePortDoctorName}</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;font-weight:100;width:20%">检查时间</span>
				<span class="am-list-item-title am-ft-ellipsis  am-ft-sm" style="font-weight:100;color:#aaa;width:20%"> ${labReport.rePortDate?datetime}</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis am-ft-sm" style="font-weight:100;">临床诊断</span>
				<span class="am-ft-ellipsis am-ft-sm" style="font-weight:100;color:#aaa;">腹部十二指肠溃疡</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis am-ft-sm" style="font-weight:100;">检验指标</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis" style="font-size:12px;font-weight:100;color:#111;width:42%">项目名称</span>
				<span class="am-list-item-title am-ft-ellipsis" style="font-size:12px;font-weight:100;color:#111;width:32%">结果</span>
				<span class="am-list-item-title am-ft-ellipsis" style="font-size:12px;font-weight:100;color:#111;font-weight:100;width:20%">参考区间</span>
			</div>
			<#list labReportDetails as labReportDetail>
			<div class="am-list-item am-list-imaginary-middle"> 
				<span class="am-list-item-title am-ft-ellipsis" style="font-size:10px;font-weight:100;color:red;width:42%">${labReportDetail.testItemName}</span>
				<span class="am-list-item-title am-ft-ellipsis" style="font-size:10px;font-weight:100;color:red;width:32%">&nbsp;${labReportDetail.testResult ! " "}</span>
				<span class="am-list-item-title am-ft-ellipsis" style="font-size:10px;font-weight:100;color:red;font-weight:100;width:20%">${labReportDetail.referenceRange}</span>
			</div>
			</#list>
			<div class="am-list-item am-list-imaginary-middle">
				<span class="am-list-item-title am-ft-ellipsis am-ft-sm" style="font-weight:100;">备注</span>
			</div>
			<div class="am-list-item am-list-imaginary-middle fn-left">
				<span style="font-size:12px;font-weight:100;color:#aaa;">说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明文字说明</span>
			</div>
		</div>
	</div>
</body>
</html>