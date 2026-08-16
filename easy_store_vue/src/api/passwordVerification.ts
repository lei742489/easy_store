import { Modal, Input } from '@arco-design/web-vue';
import { h, ref } from 'vue';

export function openPasswordModal() {
  const password = ref('');

  return new Promise<string>((resolve) => {
    let modalInstance: any = null;

    modalInstance = Modal.confirm({
      title: '密码验证',
      content: () =>
        h(Input.Password, {
          'placeholder': '敏感操作，请输入登录密码',
          'allowClear': true,
          'modelValue': password.value,
          // eslint-disable-next-line no-return-assign
          'onUpdate:modelValue': (val: any) => (password.value = val),
        }),
      onOk: () => {
        resolve(password.value);
      },
      onCancel: () => {
        resolve('');
      },
    });
  });
}

export function test() {}
