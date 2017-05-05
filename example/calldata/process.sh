cd sourcedata
split -l 200000 -d  ../data1.txt data_
sed -i "1i ph1_area_code,phone1,ph1_imei,ph1_imsi,ph2_area_code,phone2,ph2_imei,ph2_imsi,rel,call_time,call_duration" data_call*
cd ..

