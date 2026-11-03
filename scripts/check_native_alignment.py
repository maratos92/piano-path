#!/usr/bin/env python3
"""Static 16 KB ELF load alignment check for APK/AAB 64-bit native libraries."""
import struct
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as archive:
    count=0
    for name in archive.namelist():
        if not name.endswith('.so') or not any(abi in name for abi in ('arm64-v8a/', 'x86_64/')):
            continue
        data=archive.read(name)
        assert data[:4]==b'\x7fELF' and data[4]==2, f'{name}: expected ELF64'
        endian='<' if data[5]==1 else '>'
        phoff=struct.unpack_from(endian+'Q',data,32)[0]
        entrysize,countheaders=struct.unpack_from(endian+'HH',data,54)
        loads=0
        for index in range(countheaders):
            kind,flags,offset,address,physical,filesize,memsize,alignment=struct.unpack_from(endian+'IIQQQQQQ',data,phoff+index*entrysize)
            if kind==1:
                assert alignment>=16384 and (address-offset)%16384==0, f'{name}: incompatible LOAD segment'
                loads+=1
        assert loads, f'{name}: no LOAD segments'
        print(f'{name}: {loads} LOAD segments support 16 KB alignment')
        count+=1
    assert count, 'No 64-bit native libraries inspected'
    print(f'PASS: {count} native libraries; runtime 16 KB testing is still required.')
