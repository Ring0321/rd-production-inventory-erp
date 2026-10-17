"""Check screenshots captured from the real cloud UI, not generated mockups."""
from collections import Counter
import argparse
import json
from pathlib import Path
import cv2
import numpy as np
from PIL import Image

def main(OUT):
    label = np.frombuffer((OUT / '云端包件标签预览.png').read_bytes(),dtype=np.uint8)
    value, points, _ = cv2.QRCodeDetector().detectAndDecode(cv2.imdecode(label,cv2.IMREAD_COLOR))
    assert value == 'https://101.42.182.117/pkg/LD20261015A0014'
    views = []
    for name, bounds in [('云端仓储立体总览.png',(450,450,1500,901)),('云端仓储手机展示.png',(20,600,365,844))]:
        with Image.open(OUT / name) as image:
            pixels = list(image.convert('RGB').crop(bounds).get_flattened_data())
            colorful = sum(max(p)-min(p)>12 and min(p)<230 for p in pixels)
            distinct = len(Counter(pixels))
            assert colorful > 200 and distinct > 100, 'Blank 3D screenshot region: ' + name
            views.append({'file':name,'imageSize':list(image.size),'sceneColorPixels':colorful,'sceneDistinctColors':distinct,'nonblank':True})
    result = {'decodedActualCloudLabel':value,'cameraOrPrinterHardwareTested':False,'cloudScenePixels':views}
    (OUT / '页面截图与二维码解码核验.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=True))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output-dir',type=Path,required=True)
    main(parser.parse_args().output_dir)
