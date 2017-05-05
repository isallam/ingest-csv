## split the files to caller and callee 
bash-4.2$ grep callee data1.txt > data1_callee.txt
bash-4.2$ grep caller data1.txt > data1_caller.txt

## split the caller and callee files to manageable segments
bash-4.2$ split -l 500000 -d ../data1_caller.txt data1_caller_
bash-4.2$ split -l 500000 -d ../data1_callee.txt data1_callee_


## the files need headers
sed -i "1i ph1_area_code,phone1,ph1_imei,ph1_imsi,ph2_area_code,phone2,ph2_imei,ph2_imsi,rel,call_time,call_duration" data1_call*


## to ingest caller files
bash-4.2$ java -cp ../../build/libs/ingest-csv.jar com.objy.se.IngestCSV -bootfile data/testfd.boot -csvfiles "sourcedata/data1_caller_*" -mapper config/phoneCallerMapper.json

## to ingest callee files
bash-4.2$ java -cp ../../build/libs/ingest-csv.jar com.objy.se.IngestCSV -bootfile data/testfd.boot -csvfiles "sourcedata/data1_callee*" -mapper config/phoneCalleeMapper.json
