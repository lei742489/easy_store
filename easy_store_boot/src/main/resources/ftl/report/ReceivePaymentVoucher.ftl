<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta http-equiv="Content-Style-Type" content="text/css" />
    <title></title>
    <style type="text/css">
        @page {
            size: A4 portrait;
            margin: 9mm 12mm 14mm;
            @bottom-center {
                content: "第 " counter(page) " 页 / 共 " counter(pages) " 页  ${obj.createTime?string("yyyy-MM-dd HH:mm")}";
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

        .mt10 { margin-top: 4mm; }
        .mt15 { margin-top: 6mm; }
        .mt20 { margin-top: 8mm; }
        .mt30 { margin-top: 12mm; }

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
            padding: 1.2mm 1.5mm;
            font-size: 10.5pt;
            color: #000;
        }

        .tb2 .m-item {
            height: 7mm;
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
<div class="title">${userInfo.companyName!""}</div>

<div class="title mt20">收款单</div>

<table style="width: 100%;" class="s-c-c mt15">
    <tr>
        <td class="m-item">客户名称：${customer.name!""?xml}</td>
        <td class="m-item" style="text-align: center;">日期：${obj.createTime?string("yyyy/MM/dd")}</td>
        <td class="m-item" style="text-align: right;">单号: ${obj.orderNo!""?xml}</td>
    </tr>
</table>

<table class="tb2 mt10" style="width: 100%; border-collapse: collapse;" border="1">
    <tr>
        <td class="m-item" style="width: 65px;">行号</td>
        <td class="m-item">帐户名称</td>
        <td class="m-item" style="width: 12%;">金额</td>
        <td class="m-item">备注</td>
    </tr>
    <#list obj.settleItems as row>
        <tr>
            <td class="m-item" style="width: 65px;">${row_index + 1}</td>
            <td class="m-item">${row.settleId!""?xml}</td>
            <td class="m-item" style="width: 12%;">￥${row.amount?string("0.00")}</td>
            <td class="m-item">${row.note!""?xml}</td>
        </tr>
    </#list>
    <tr>
        <td class="m-item"  style="text-align: center; ">
            合计
        </td>
        <td class="m-item" style="text-align: left;padding-left: 14px;">${obj.totalAmountChinese!""?xml}</td>
        <td class="m-item" >￥${total?string("0.00")}</td>
        <td class="m-item"></td>
    </tr>
</table>

<div class="sub-1 mt20">剩余欠款：￥${customer.payable?string("0.00")}</div>
<div class="sub-1 mt20">备注：${obj.note!""?xml}</div>
<div class="sub-1 mt30">签收人：</div>

</body>
</html>
