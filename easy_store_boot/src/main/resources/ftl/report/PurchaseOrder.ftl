<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta http-equiv="Content-Style-Type" content="text/css" />
    <title></title>
    <style type="text/css">
        @page {
            size: A4 landscape;
            margin: 7mm 8mm 12mm;
            @bottom-center {
                content: "第 " counter(page) " 页 / 共 " counter(pages) " 页   ${obj.createTime?string("yyyy-MM-dd HH:mm")}";
                font-family: SimSun;
                font-size: 8pt;
                color: #000;
            }
        }

        body {
            font-family: SimSun;
            margin: 0;
            padding: 0;
            color: #000;
            font-size: 10.5pt;
            line-height: 1.25;
        }

        div {
            width: 100%;
        }

        .mt10 {
            margin-top: 2mm;
        }

        .mt15 {
            margin-top: 3mm;
        }

        .mt20 {
            margin-top: 4mm;
        }

        .title {
            font-size: 16pt;
            font-weight: bold;
            color: #000;
            text-align: center;
        }

        .sub-1 {
            font-size: 10.5pt;
            color: #000;
            font-weight: normal;
        }

        .m-item {
            padding: 0.8mm 1.5mm;
            font-size: 10.5pt;
            color: #000;
        }

        .tb2 .m-item {
            height: 6mm;
            vertical-align: middle;
            text-align: center;
        }

        .tb2 {
            table-layout: fixed;
            border-color: #000;
        }
    </style>
</head>


<body>
<!--
<div class="title">${userInfo.companyName!""}</div>

<div class="sub-1 mt15">店铺地址：${userInfo.address!""}</div>
<div class="sub-1 mt15">联系方式：${userInfo.mobile!""}</div> -->

<div class="title mt10" style="">${userInfo.companyName!""} 进货单</div>

<table style="width: 100%;" class="s-c-c mt10">
    <tr>
        <td class="m-item">供应商：${obj.supplierId}</td>
        <td class="m-item" style="text-align: left;">单号： ${obj.orderNo}</td>
        <td class="m-item" style="text-align: center;">日期：${obj.createTime?string("yyyy/MM/dd")}</td>
        <!-- <td class="m-item" style="text-align: right;">币种：人民币</td> -->
    </tr>
</table>

<table class="tb2 mt10" style="width: 100%; border-collapse: collapse;" border="1">
    <tr>
        <td class="m-item" style="width: 65px;">行号</td>
        <td class="m-item" style="width: 35%;">品名规格</td>
        <td class="m-item" style="width: 8%;">单位</td>
        <td class="m-item" style="width: 8%;">数量</td>
        <td class="m-item">单价</td>
        <td class="m-item">金额</td>
        <td class="m-item">备注</td>
    </tr>
    <#list obj.items as row>
        <tr>
            <td class="m-item" style="width: 65px;">${row_index + 1}</td>
            <td class="m-item" style="width: 35%;">${row.goodsId!""}</td>
            <td class="m-item" style="width: 8%;">${row.unit!""}</td>
            <td class="m-item" style="width: 8%;">${row.quantity}</td>
            <td class="m-item">${row.unitPrice?string("0.00")}</td>
            <td class="m-item">${row.totalAmount?string("0.00")}</td>
            <td class="m-item">${row.note!""}</td>
        </tr>
    </#list>
    <tr>
        <td class="m-item" colspan="5" style="text-align: left;padding-left: 16px;">
            合计 金额大写 ${obj.totalAmountChinese!""}
        </td>
        <td class="m-item">￥${obj.totalAmount?string("0.00")}</td>
        <td class="m-item"></td>
    </tr>
</table>

<table style="width: 100%;" class="s-c-c mt10">
    <tr>
        <td class="m-item">折后金额：￥${obj.discountedAmount?string("0.00")}</td>
        <td class="m-item" style="text-align: left;">折扣率：${obj.discountRate?string("0.00")}%</td>
        <td class="m-item" style="text-align: center;">运费：￥${obj.freightAmount?string("0.00")}</td>
        <td class="m-item" style="text-align: right;">本单应付：￥${obj.payableAmount?string("0.00")}</td>
        <td class="m-item" style="text-align: right;">实 付：￥${obj.paidAmount?string("0.00")}</td>
    </tr>
</table>

<table style="width: 100%;" class="mt15">
    <tr>
        <td class="sub-1" style="width: 20%;">本单欠款：￥${obj.unpaidAmount?string("0.00")} </td>
        <td class="sub-1" style="text-align: left;">供应商总欠款：￥${supplier.payable?string("0.00")} </td>
    </tr>
</table>

<div class="sub-1 mt15">备注说明：${obj.note!""}</div>

</body>

</html>
