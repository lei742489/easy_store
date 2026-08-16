<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta http-equiv="Content-Style-Type" content="text/css" />
    <title></title>
    <style type="text/css">
        @page {
            size: A4 landscape;
            margin: 8mm;

            @bottom-center {
                content: "第 " counter(page) " 页 / 共 " counter(pages) " 页  ${obj.createTime?string("yyyy-MM-dd HH:mm")}";
                font-family: SimSun;
                font-size: 12pt;
                color: #222;
            }
        }

        body {
            font-family: SimSun;
            padding: 2px;
            position: relative;
            height: 100%;
        }

        div {
            width: 100%;
        }

        .mt10 { margin-top: 10px; }
        .mt15 { margin-top: 15px; }
        .mt20 { margin-top: 20px; }
        .mt30 { margin-top: 30px; }

        .title {
            font-size: 24px;
            font-weight: 600;
            color: #111;
            text-align: center;
        }

        .sub-1 {
            font-size: 17px;
            color: #333;
            font-weight: 550;
        }

        .m-item {
            font-size: 17px;
            color: #333;
        }

        .tb2 .m-item {
            height: 33px;
            vertical-align: middle;
            text-align: center;
        }

        .footer {
            width: 100%;
            text-align: center;
            font-size: 15px;
            color: #222;
            position: absolute;
            bottom: 0;
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
