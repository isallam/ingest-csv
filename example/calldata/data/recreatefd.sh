objy deletefd -bootfile testfd.boot
objy createfd -fdname testfd
objy do -infile ../config/schema.txt -boot testfd.boot 
objy addIndex -name phoneIndex -class Phone -attribute phoneNumber  -bootfile testfd.boot
objy importPlacement -infile calldata.pmd -boot testfd.boot

