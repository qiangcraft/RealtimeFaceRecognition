# Realtime Face Recognition on Android Devices


This sample demonstrates realtime face recognition on Android. The project is based on the [FaceNet](https://arxiv.org/abs/1503.03832) now. 
FaceNet output the image's face feature embedding vector.
We store only one picture of the same person. We compare the detected real-time face feature with the FaceDB's face feature by computing two faces' Euclidean distance.If The distance is smaller than THRESHOLD, and  it is the minimum compared result, we consider they are the same person.

## Inspiration
The project is heavily inspired by
* [FaceNet](https://github.com/davidsandberg/facenet)
* [MTCNN](https://github.com/blaueck/tf-mtcnn)
* [Tensorflow Android Camera Demo](https://github.com/tensorflow/tensorflow/tree/master/tensorflow/examples/android)
* [Face-recognizer-android](https://github.com/pillarpond/face-recognizer-android)
## Function
* You can click the '+ 'button ,then add the new person's face image and name that you want to recognize.[(Source)](https://github.com/qiangz520/RealtimeFaceRecognition/blob/master/app/src/main/assets/label)
8 You can click one person's name in that list to delete his face data.

## Pre-trained model
from davidsandberg's facenet

| Model name      | LFW accuracy | Training dataset | Architecture |
|-----------------|--------------|------------------|-------------|
| [20180402-114759](https://drive.google.com/open?id=1EXPBSXwTaqrSC0OhUdXNmKSh9qJUQ55-) | 0.9965        | VGGFace2      | [Inception ResNet v1](https://github.com/davidsandberg/facenet/blob/master/src/models/inception_resnet_v1.py) |

## License
[Apache License 2.0](./LICENSE)

## To-do works
* Use more accurate, lighter and faster face recognition model
* Use better and faster face detector