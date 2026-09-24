import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button, Modal } from 'antd'
import { ScanOutlined } from '@ant-design/icons'

/**
 * 移动端 H5 共享组件（二期扫码作业）。
 * <p>
 * 桌面端布局之外独立使用：小屏优先（max-width 480px 居中）、大按钮、AntD 5 + Tailwind，
 * 不引入任何新依赖。复用桌面端同一套 axios 封装与登录态 store。
 */

/** 移动端页面外壳：全屏浅灰底 + 内容限宽居中 */
export function MobileShell({
  title,
  children,
  extra,
}: {
  title: string
  children: ReactNode
  extra?: ReactNode
}) {
  return (
    <div className="min-h-screen bg-gray-100" style={{ paddingBottom: 32 }}>
      <div className="mx-auto" style={{ maxWidth: 480 }}>
        <div
          className="flex items-center justify-between px-4 py-3 text-white"
          style={{ background: '#1668dc' }}
        >
          <div className="text-base font-semibold">{title}</div>
          {extra}
        </div>
        <div className="p-3">{children}</div>
      </div>
    </div>
  )
}

// ===================== 摄像头扫码（可选增强，核心是手工输入） =====================

/** BarcodeDetector 尚未进入 TS lib.dom 的最小类型声明（Chrome/Edge 支持，Safari 不支持则降级为纯预览） */
interface DetectedBarcode {
  rawValue: string
}
interface BarcodeDetectorLike {
  detect(source: HTMLVideoElement): Promise<DetectedBarcode[]>
}
type BarcodeDetectorCtor = new (options?: { formats?: string[] }) => BarcodeDetectorLike

/** 打开摄像头并尝试持续解码；不支持解码能力的浏览器仅作取景预览 */
export function ScanCameraButton({ onDetected }: { onDetected: (code: string) => void }) {
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const timerRef = useRef<number | null>(null)

  const stop = () => {
    if (timerRef.current != null) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }

  useEffect(() => {
    if (!open) {
      stop()
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
        })
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        streamRef.current = stream
        if (videoRef.current) {
          videoRef.current.srcObject = stream
          await videoRef.current.play()
        }
        // 支持 BarcodeDetector 的浏览器每 400ms 解码一次；不支持的仅预览
        const ctor = (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector
        if (ctor) {
          const detector = new ctor({
            formats: ['code_128', 'code_39', 'ean_13', 'ean_8', 'qr_code'],
          })
          timerRef.current = window.setInterval(async () => {
            if (!videoRef.current) return
            try {
              const codes = await detector.detect(videoRef.current)
              if (codes.length > 0) {
                onDetected(codes[0].rawValue)
                setOpen(false)
              }
            } catch {
              /* 帧未就绪等瞬时错误忽略 */
            }
          }, 400)
        } else {
          setError('当前浏览器不支持自动识码，请对准条码人工输入')
        }
      } catch {
        setError('无法访问摄像头（需 HTTPS 或本地环境授权）')
      }
    })()
    return () => {
      cancelled = true
      stop()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  return (
    <>
      <Button
        block
        size="large"
        icon={<ScanOutlined />}
        onClick={() => {
          setError(null)
          setOpen(true)
        }}
      >
        摄像头扫码（可选）
      </Button>
      <Modal
        title="对准条码"
        open={open}
        footer={null}
        destroyOnClose
        onCancel={() => setOpen(false)}
      >
        <video ref={videoRef} className="w-full" playsInline muted style={{ background: '#000' }} />
        {error ? <p className="mt-2 text-xs text-gray-500">{error}</p> : null}
      </Modal>
    </>
  )
}
